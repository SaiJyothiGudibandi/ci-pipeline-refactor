/* Copyright 2020 Google LLC. This software is provided as is, without warranty or representation for any use or purpose.
 * Your use of it is subject to your agreement with Google.
 */
import google.utils.GlobalUtils
def call(Map config) {
    def tags
    def sha
    def branch
    def built = false
    def published_to_dev
    try {
        node {
            step([$class: 'WsCleanup'])
            stage("Setup") {
                checkout scm
                sh "git fetch"
                sha = utils.getCommitSha()
                tags = utils.getGitTags(sha)
                branch = env.BRANCH_NAME ? "${env.BRANCH_NAME}" : scm.branches[0].name
                currentBuild.description = "GIT_COMMIT=${sha},GIT_BRANCH=${branch}"
                echo "TAGS = ${tags.toString()}"
                sh("env|sort")
            }
        }
        // Build logic (If no tag is found we just build)
        if (!GlobalUtils.isThereTag('dev-', tags) && !GlobalUtils.isThereTag('rel-', tags) && !GlobalUtils.isThereTag('deploy-dev-', tags)) {
            buildStages(config)
            built = true
        }
        //Publish to dev logic (if dev-* is found we publish to dev repo)
        if (GlobalUtils.isThereTag('dev-', tags) && !GlobalUtils.isThereTag('rel-', tags)) {
            if (!GlobalUtils.findArtifact(config.env.JOB_NAME, sha, config.env.BUILD_NUMBER) && !built) {
                buildStages(config)
                node {
                    stage("Publish Artifact") {
                        copyArtifacts(projectName: config.env.JOB_NAME, selector: specific("${config.env.BUILD_NUMBER}"));
                        unzip dir: ".", zipFile: "testing-archive.zip"
                        echo("Upload To Dev Artifactory")
                        currentBuild.description = currentBuild.description << ",PUBLISHED=dev"
                        published_to_dev = true
                    }
                }
            } else {
                def repo = "dev"
                publishStages(config, sha, repo)
                published_to_dev = true
            }
        }
        // Ff "deploy-dev-* tag is found we deploy to dev environment but only if the artifact was previously published)
        // If deployment has already been done in previous builds then we don't do anything
        if (GlobalUtils.isThereTag('deploy-dev-', tags) && !GlobalUtils.wasDeployed(config.env.JOB_NAME, sha)) {
            if (GlobalUtils.isPublished(config.env.JOB_NAME, sha, "dev") || published_to_dev) {
                deployStages(config, sha)
            } else {
                node {
                    stage("Failed to deploy") {
                        currentBuild.result = "FAILURE"
                        utils.updateGithubCommitStatus(currentBuild)
                        throw ("Can not deploy unpublished artifact to verify repo")
                    }
                }
            }
        }

        // Publish/Promote to verify repo. We publish/promote the artifact only if it has been successfully deployed to dev
        if (GlobalUtils.isThereTag('rel-', tags) && branch == config.release_branch) {
            //If the build has been created, pushed to dev, and deployed successfully then release to verify
            if (GlobalUtils.wasDeployed(config.env.JOB_NAME, sha)) {
                def repo = "verify"
                publishStages(config, sha, repo)
            } else {
                node {
                    stage("Failed to publish") {
                        currentBuild.result = "FAILURE"
                        utils.updateGithubCommitStatus(currentBuild)
                        throw ("Can not publish untested artifact to verify repo")
                    }
                }
            }
        }

        node {
            stage("Update Git Commit Result") {
                chkout()
                currentBuild.result = "SUCCESS"
                utils.updateGithubCommitStatus(currentBuild)
            }
        }
    } catch(e){
        currentBuild.result = "FAILURE"
        utils.updateGithubCommitStatus(currentBuild)
        throw e
    }
}

def buildStages(config){
    def builders = [:]
    builders["build"] = {
        customBuild {
            yamlConfig = "${config.build_config}"
        }
    }
    builders["unit-test"] = {
        customTest {
            yamlConfig = "${config.test_config}"
        }
    }
    builders["scan_code"] = {
        scanCode(["sonar.projectKey": "example-pipeline-sample-app:project"])
    }
    parallel builders
}

def publishStages(config, sha, repo){
    if (!GlobalUtils.isPublished(config.env.JOB_NAME, sha, repo)) {
        node {
            stage("Publish ${repo} Artifact") {
                def artifact = GlobalUtils.findArtifact(config.env.JOB_NAME, sha, config.env.BUILD_NUMBER)
                if (!artifact) {
                    currentBuild.result = "FAILURE"
                    utils.updateGithubCommitStatus(currentBuild)
                    throw ("Artifact for ${sha} not found in older builds")
                }
                copyArtifacts(projectName: config.env.JOB_NAME, selector: specific("${artifact.build_number}"));
                unzip dir: ".", zipFile: "${artifact.path}"
                echo("Upload To ${repo} Artifactory")
                currentBuild.description = currentBuild.description << ",PUBLISHED=${repo}"
            }
        }
    }
}

def deployStages(config, sha){
    def publishers = [:]
    publishers["docker"] = {
        node {
            stage("Build Docker Image") {
                echo "Build Docker"
            }
            stage("Publish Docker Image") {
                echo "Publish Docker"
            }
        }
    }
    publishers["helm-chart"] = {
        node {
            stage("Build Helm Chart") {
                echo "Build Helm Chart"
            }
            stage("Publish Helm Chart") {
                echo "Publish Helm Chart"
            }
        }
    }
    parallel publishers
    node {
        echo "Trigger deployment by making a commit on dev branch in deploy repo"
        utils.createCDgitCommit(config.cd_repo, config.cd_branch, sha, config.cd_git_user, config.cd_git_credentials)
        utils.getCDResult(sha, config.cd_branch, "mock-cd-pipeline")
        currentBuild.description = currentBuild.description << ",DEPLOYED=true"
    }
}
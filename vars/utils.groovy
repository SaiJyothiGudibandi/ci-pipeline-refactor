import google.utils.GlobalUtils

/* Copyright 2020 Google LLC. This software is provided as is, without warranty or representation for any use or purpose.
 * Your use of it is subject to your agreement with Google.
 */

def getRepoURL() {
    sh "git config --get remote.origin.url > .git/remote-url"
    return readFile(".git/remote-url").trim()
}

def getCommitSha() {
    sh (
            script: "git rev-parse --verify HEAD",
            returnStdout: true
    ).trim()
}

def getGitTags(sha) {
    // return a list of current git tags on this commit.
    sh (
            script: "git tag --points-at ${sha}",
            returnStdout: true
    ).trim().split()
}

def updateGithubCommitStatus(build) {
    // workaround https://issues.jenkins-ci.org/browse/JENKINS-38674
    repoUrl = getRepoURL()
    commitSha = getCommitSha()

    step([
            $class: 'GitHubCommitStatusSetter',
            reposSource: [$class: "ManuallyEnteredRepositorySource", url: repoUrl],
            commitShaSource: [$class: "ManuallyEnteredShaSource", sha: commitSha],
            errorHandlers: [[$class: 'ShallowAnyErrorHandler']],
            statusResultSource: [
                    $class: 'ConditionalStatusResultSource',
                    results: [
                            //[$class: 'BetterThanOrEqualBuildResult', result: 'SUCCESS', state: 'SUCCESS', message: customBuild.description],
                            //[$class: 'BetterThanOrEqualBuildResult', result: 'FAILURE', state: 'FAILURE', message: customBuild.description],
                            [$class: 'AnyBuildResult', state: build.result, message: build.description]
                    ]
            ]
    ])
}

def executeBuildConfig(build_info){
    def artifacts = []
    build_info.eachWithIndex { it, i ->
        _validate(it)
        echo("Executing ${it["name"]}")
        source = it.containsKey("source") ? it["source"] : "."
        if (it.containsKey("image") && it.containsKey("commands")) {
            dir(source) {
                if (it["image"].containsKey("Dockerfile")) {
                    dockerfile = new File(it["image"]["Dockerfile"])
                    dockerfile_name = dockerfile.getName()
                    //path with respect to it["source"]
                    dockerfile_path = dockerfile.getPath()
                    dockerfile_directory = dockerfile_path[0..(dockerfile_path.size() - dockerfile_name.size() - 1)]
                    if (dockerfile_path == dockerfile_directory) {
                        echo "Dockerfile is at the source directory"
                        dockerfile_directory = "."
                    }
                    build_params = "-f ${dockerfile_path} ${_dockerParams(it, "docker_build_params")} ${dockerfile_directory}"
                    docker.build("${it["name"]}", build_params).inside(_dockerParams(it, "docker_run_params")) {
                        _executeShellCommands(it["commands"])
                    }
                } else {
                    docker.image(it["image"]["name"]).inside(_dockerParams(it, "docker_run_params")) {
                        _executeShellCommands(it["commands"])
                    }
                }
            }
        }
        if (it.containsKey("artifacts")){
            it["artifacts"].each { artifact, artifact_index ->
                stash_name = "${it["name"]}-${artifact_index}"
                stash name: stash_name, includes: artifact
                artifacts.add(stash_name)
            }
        }
        if (it.containsKey("junit")){
            junit it["junit"]
        }
    }
    if (artifacts != []){
        node {
            stage("Archive") {
                sh "mkdir artifacts"
                dir("artifacts") {
                    artifacts.each { stash ->
                        unstash stash
                    }
                    zip zipFile: "testing-archive.zip", archive: true, dir: "."
                }
            }
        }
    }
}

def createCDgitCommit(url, branch, sha, cd_git_user, cd_git_credentials){
    return stage("Trigger CD using a git commit"){
        step([$class: 'WsCleanup'])
        withCredentials([sshUserPrivateKey(credentialsId: cd_git_credentials, keyFileVariable: 'SSH_KEY')]) {
            sh("git clone ${url}")
            dir("jenkins-mock-cd"){
                sh "git config user.email \"${cd_git_user.email}\""
                sh "git config user.name \"${cd_git_user.name}\""
                sh("git checkout ${branch}")
                sh("git remote -v")
                sh("git status")
                sh "echo ${sha} >> trigger-commits.txt"
                sh "git add ."
                sh "git commit -am \"sha=${sha}\""
                sh("git push origin ${branch}:${branch}")
            }
        }
    }
}

def getCDResult(sha, branch, job_name){
    return stage("Get results from CD"){
        echo "Waiting for results"
        echo "Deciding on results"
    }
}
def _dockerParams(build_step, params){
    return build_step["image"].containsKey(params) ? build_step["image"]["params"] : ""
}

def _executeShellCommands(shell_commands){
    shell_commands.each { command ->
        echo("Running ${command}")
        sh script:"${command}"
    }
}

def _validate(build_step){
    if (! build_step.containsKey("name")){
        throw("Build step must have a name")
    }
    if((!build_step.containsKey("image") && build_step.containsKey("commands")) || (build_step.containsKey("image") && !build_step.containsKey("commands"))){
        throw("if you define image then you have to define commands and vice versa")
    }
}
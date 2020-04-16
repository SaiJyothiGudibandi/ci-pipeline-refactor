package google.utils

import hudson.model.*
import hudson.model.Result
import hudson.model.Run
import hudson.model.Run.Artifact

class GlobalUtils {
    public static String GIT_COMMIT = "GIT_COMMIT"
    public static String PUBLISHED = "PUBLISHED"
    public static String DEPLOYED = "DEPLOYED"

    public static def getAllBuilds(job_name){
        def job = getJob(job_name)
        def builds = job.getBuilds()
        def str = ""
        builds.each { build ->
            def status = build.getBuildStatusSummary().message
            str = str << "Build: ${build} | Status: ${status} | Description: ${build.description}\n"
        }
        return str.toString()
    }

    public static def isThereTag(String tag, String[] tags){
        for(t in tags){
            if (t.startsWith(tag)){
                return true
            }
        }
        return false
    }

    public static def findArtifact(String job_name, String sha, String current_build_number){
        def job = getJob(job_name)
        def builds = job.getBuilds()
        def artifact_config = [:]
        for (build in builds) {
            if (build.number == current_build_number.toInteger()) {
                continue
            }
            if (getCommitFromDescription(build.description) == sha && build.getResult() == Result.SUCCESS){
                for (artifact in build.getArtifacts()) {
                    artifact_config["path"] = artifact.relativePath
                    artifact_config["build_number"] = build.number
                }
                if (artifact_config) {
                    return artifact_config
                }
            }
        }
        return null
    }

    public static def isPublished(String job_name, String sha, String repo){
        def job = getJob(job_name)
        def builds = job.getBuilds()
        for (build in builds) {
            if (getCommitFromDescription(build.description) == sha && isPublishedFromDescription(build.description, repo)){
                return true
            }
        }
        return false
    }

    public static def wasDeployed(String job_name, String sha){
        def job = getJob(job_name)
        def builds = job.getBuilds()
        for (build in builds) {
            if (getCommitFromDescription(build.description) == sha){
                for (desc in build.description.split(",")){
                    if (desc.split("=")[0] == this.DEPLOYED && desc.split("=")[1] == "true"){
                        return true
                    }
                }
            }
        }
        return false
    }

    private static def getCommitFromDescription(String description){
        if (!description){
            return "GIT SHA NOT FOUND"
        }
        for (desc in description.split(",")){
            if (desc.split("=")[0] == this.GIT_COMMIT){
                return desc.split("=")[1]
            }
        }
        return "GIT SHA NOT FOUND"
    }

    private static def isPublishedFromDescription(String description, String repo){
        if (!description){
            return false
        }
        for (desc in description.split(",")){
            if (desc.split("=")[0] == this.PUBLISHED && desc.split("=")[1] == "${repo}"){
                return true
            }
        }
        return false
    }

    private static def getJob(job_name){
        def job_n = job_name.toString().split("/")[0]
        def branch_name = job_name.toString().split("/")[1]
        for (job in Hudson.instance.getItem(job_n).items){
            if (job.name == branch_name) {
                return job
            }
        }
        return null
    }
}
/* Copyright 2020 Google LLC. This software is provided as is, without warranty or representation for any use or purpose.
 * Your use of it is subject to your agreement with Google.
 */

@Library('sample-pipeline@ci-pipeline-refactor') _
properties([pipelineTriggers([githubPush()])])
examplePipeline build_config: "resources/build-info.yaml",
 test_config: "resources/test-info.yaml",
 release_branch: "dev" ,
 env: env,
 cd_repo: "git@github.com:ops-guru/jenkins-mock-cd.git",
 cd_branch: "dev",
 cd_git_user: ["name": "Jenkins Worker", "email": "vlahovic@google.com"],
 cd_git_credentials: "github-ssh-marko7460"
/* Copyright 2020 Google LLC. This software is provided as is, without warranty or representation for any use or purpose.
 * Your use of it is subject to your agreement with Google.
 */

def call(body) {
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()
    def yaml_file = config.yamlConfig
    def build_info = []
    return node {
        chkout()
        ansiColor("xterm"){
            echo("YAML FILE ${yaml_file}")
            if (yaml_file == ""){
                build_info = readYaml file: "build-info.yaml"
            } else {
                build_info = readYaml file: yaml_file
            }
            stage("Test"){
                utils.executeBuildConfig(build_info)
            }
        }
    }
}
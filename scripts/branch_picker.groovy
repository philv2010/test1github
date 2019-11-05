properties([parameters([[$class: 'GitParameterDefinition', branch: '', branchFilter: '.*', defaultValue: '', description: '', name: 'Branch', quickFilterEnabled: false, selectedValue: 'TOP', sortMode: 'DESCENDING_SMART', tagFilter: '*', type: 'PT_BRANCH']])])
node ("master") {
    def WORKING_DIR = pwd()
    def gitRepoUrl = 'git@github.com:philv2010/microservice-pipelines.git'
    deleteDir()
    stage ("checkout") {
        println ("Branch = ${Branch}")
        // checkout master if Branch Picker doesn't populate (then stop do not deploy master)
        if (Branch.contains("No Git")) {
            checkout([$class: 'GitSCM', 
                branches: [[name: '*/master']], 
                doGenerateSubmoduleConfigurations: false, extensions: [
                    [$class: 'CleanBeforeCheckout'], 
                    [$class: 'PruneStaleBranch'], 
                    [$class: 'CheckoutOption', timeout: 15], 
                    [$class: 'CloneOption', depth: 2, noTags: false, reference: '', shallow: true, timeout: 45]], 
                userRemoteConfigs: [[credentialsId: '8851f4a3-6599-4b2c-af49-5c58bcad1704', url: gitRepoUrl]]
                ]);
            try {
                println "Root checkout completed to populate Branch List"
                error 'FAIL'
            }
            catch (e) {
                currentBuild.result = 'UNSTABLE'
            }
        }
        // checkout the selected Branch
        else {
            checkout([$class: 'GitSCM', 
                branches: [[name: Branch]], 
                doGenerateSubmoduleConfigurations: false, extensions: [
                    [$class: 'CleanBeforeCheckout'], 
                    [$class: 'PruneStaleBranch'], 
                    [$class: 'CheckoutOption', timeout: 15], 
                    [$class: 'CloneOption', depth: 2, noTags: false, reference: '', shallow: true, timeout: 45]], 
                userRemoteConfigs: [[credentialsId: '8851f4a3-6599-4b2c-af49-5c58bcad1704', url: gitRepoUrl]]
                ]);
        }
    }
}

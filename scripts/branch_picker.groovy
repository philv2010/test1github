properties([parameters([[$class: 'GitParameterDefinition', branch: '', branchFilter: '.*', defaultValue: '', description: '', name: 'Branch', quickFilterEnabled: false, selectedValue: 'TOP', sortMode: 'DESCENDING_SMART', tagFilter: '*', type: 'PT_BRANCH']])])
node ("master") {
    def WORKING_DIR = pwd()
    def gitRepoUrl = 'git@github.ford.com:B2CGlobal2/quicklane-global.git'
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
                userRemoteConfigs: [[credentialsId: '89cacb81-0aa9-47d2-88b2-7734e3e2ba5d', url: gitRepoUrl]]
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
                userRemoteConfigs: [[credentialsId: '89cacb81-0aa9-47d2-88b2-7734e3e2ba5d', url: gitRepoUrl]]
                ]);
        }
    }
}
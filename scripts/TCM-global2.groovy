properties([ buildDiscarder(logRotator(artifactDaysToKeepStr: '', artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '10')), parameters([credentials(credentialType: 'com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl', defaultValue: 'b5ac603a-6b93-4255-bcaf-eeed3e27698e', description: 'TCM AEM Deploy User', name: 'JENKINS_DEPLOY_USER', required: true)]), ([[$class: 'GitParameterDefinition', branch: '', branchFilter: '.*', defaultValue: '', description: '', name: 'Branch', quickFilterEnabled: false, selectedValue: 'TOP', sortMode: 'DESCENDING_SMART', tagFilter: '*', type: 'PT_BRANCH']])])

node("master") {
    def workspace = pwd()
        def gitRepoUrl = 'git@github.com:philv2010/quicklane-global.git'
        def branch = "${Branch}"

        //Global Variables
        def AEM_AUTHOR = 'http://tcmauthor.tcmech.net' //Author Server
        def PACKAGE_PATH_APP = "${workspace}/quicklane-global/build/distributions" // Path to application package in workspace
        def PACKAGE_NAME_APP = 'quicklane-global-full' // Application Package name
        def PACKAGE_LOCATION = 'com.ford' //package group in AEM crx/packmgr

        try{
        stage ("checkout") {
                deleteDir()
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
                                userRemoteConfigs: [[credentialsId: '1c00988b-3bae-4a5c-a1b8-19b559b56dcc', url: gitRepoUrl]]
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
                                userRemoteConfigs: [[credentialsId: '1c00988b-3bae-4a5c-a1b8-19b559b56dcc', url: gitRepoUrl]]
                        ]);
            sh """eval `ssh-agent -s`
               ssh-add -D
               ssh-add ~/.ssh/tcm-global
               git clone ${gitRepoUrl}
               cd quicklane-global
               git checkout ${branch}"""
        }
}
        stage ("Build") {
                        def gradle_version = "G-4.10"
                withEnv( ["PATH+GRADLE=${tool gradle_version}/bin"] ){
                                sh "~/tools/hudson.plugins.gradle.GradleInstallation/G-4.10/bin/gradle --build-file=${workspace}/quicklane-global/build.gradle build"
                        }
                }
    }
    catch (err) {
        currentBuild.result = 'FAILURE'
        throw new hudson.AbortException('Something went Wrong')
    }

        // establish deployment credentials with masking
        withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: JENKINS_DEPLOY_USER, usernameVariable: 'AEM_USERNAME', passwordVariable: 'AEM_PASSWORD']]) {
                def VERSION = sh returnStdout: true, script: "head -n10 ${workspace}/quicklane-global/build.gradle | grep \"version\" | cut -d \"=\" -f 2 | cut -d \"'\" -f 2"
                VERSION = VERSION.trim()

                //Upload application to Author
                stage("Author Upload and Install Application") {
                        //concatinate package name
                        def PACKAGE_NAME = "${PACKAGE_NAME_APP}-${VERSION}.zip"
                        //Upload
                        def OUTPUT = sh returnStdout: true, script: "curl -H \"Accept: application/json\" -u ${AEM_USERNAME}:${AEM_PASSWORD} -F package=@\"${PACKAGE_PATH_APP}/${PACKAGE_NAME}\" ${AEM_AUTHOR}/crx/packmgr/service/.json/ -F cmd=upload -F force=true"
                        // Error check on application Author upload
                        if (OUTPUT.contains("success\":true")){
                                println OUTPUT
                        }
                        else {
                                println OUTPUT
                                currentBuild.result = 'FAILURE'
                                throw new hudson.AbortException('Application Author Upload Failed')
                        }
                        //install application to Author
                        OUTPUT = sh returnStdout: true, script: "curl -H \"Accept: application/json\" -u ${AEM_USERNAME}:${AEM_PASSWORD} -X POST ${AEM_AUTHOR}/crx/packmgr/service/.json/etc/packages/${PACKAGE_LOCATION}/${PACKAGE_NAME} -F cmd=install"
                        // Error check application Install on author
                        if (OUTPUT.contains("success\":true")){
                                println OUTPUT
                        }
                        else {
                                println OUTPUT
                                currentBuild.result = 'FAILURE'
                                throw new hudson.AbortException('Application Author Install Failed')
                        }
                }

                //Replicate Application to Publish
                stage("Replicate Application to Publish") {
                        //concatinate package name
                        def PACKAGE_NAME = "${PACKAGE_NAME_APP}-${VERSION}.zip"
                        //Upload application to publisher
                        def OUTPUT = sh returnStdout: true, script: "curl -H \"Accept: application/json\" -u ${AEM_USERNAME}:${AEM_PASSWORD} -X POST ${AEM_AUTHOR}/crx/packmgr/service/.json/etc/packages/${PACKAGE_LOCATION}/${PACKAGE_NAME} -F cmd=replicate"
                        //error check application publisher upload
                        if (OUTPUT.contains("success\":true")){
                                println OUTPUT
                        }
                        else {
                                println OUTPUT
                                currentBuild.result = 'FAILURE'
                                throw new hudson.AbortException('Application Replicate Failed')
                        }
                }
        }
}

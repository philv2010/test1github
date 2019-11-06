properties([ buildDiscarder(logRotator(artifactDaysToKeepStr: '', artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '10')), parameters([credentials(credentialType: 'com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl', defaultValue: '4debb9c2-6fff-40de-bf84-a5f7862bcf84', description: 'TCM AEM Deploy User', name: 'JENKINS_DEPLOY_USER', required: true)])])

node("master") {
    def workspace = pwd() 
	def gitRepoUrl = 'git@github.com:philv2010/tcm-global.git'
	def branch = 'develop'

	//Global Variables
	def AEM_AUTHOR = 'http://tcmauthor.tcmech.net' //Author Server
	def PACKAGE_PATH_APP = "${workspace}/quicklane-global/build/distributions" // Path to application package in workspace
	def PACKAGE_NAME_APP = 'quicklane-global-full' // Application Package name
	def PACKAGE_LOCATION = 'com.tcm' //package group in AEM crx/packmgr

	try{
        stage ("checkout") {
        	deleteDir()
            sh """eval `ssh-agent -s`
               ssh-add -D
               ssh-add ~/.ssh/tcm-global
               git clone ${gitRepoUrl}
               cd tcm-global
               git checkout ${branch}"""
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
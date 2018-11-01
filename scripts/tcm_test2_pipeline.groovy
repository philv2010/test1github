//Global Variable Declaration:
def AEM_AUTHOR = 'http://192.168.1.86:4502'
def AEM_PUBLISHER = 'http://192.168.1.86:4503'
def PACKAGE_PATH_APP = '/var/lib/jenkins/jobs/test1github/workspace/content/target'
def PACKAGE_PATH_BND =  '/var/lib/jenkins/jobs/test1github/workspace/bundle/target'
def PACKAGE_NAME_APP = 'tcm-application'
def PACKAGE_NAME_BND = 'tcm-bundle'
def PACKAGE_LOCATION = 'adobe/consulting' //Package group in AEM 
def AEM_BUNDLENAME = 'com.adobe.acs.tcm-bundle'

node("master") {
	def workspace = pwd() 
	def gitRepoUrl = 'ssh://git@github.com:philv2010/test1github.git'
	def branch = '*/test1'
	
	try{
        stage ("checkout") {
            checkout([$class: 'GitSCM', 
				branches: [[name: branch]], 
				doGenerateSubmoduleConfigurations: false, extensions: [
					[$class: 'CleanBeforeCheckout'], 
					[$class: 'PruneStaleBranch'], 
					[$class: 'CheckoutOption', timeout: 15], 
					[$class: 'CloneOption', depth: 2, noTags: false, reference: '', shallow: true, timeout: 45]], 
				userRemoteConfigs: [[credentialsId: env.jenkinsCredentialsId, url: gitRepoUrl]]
			]);
        }
        stage ("Build") {
            def mvn_version = 'm3.2.5'
            withEnv( ["PATH+MAVEN=${tool mvn_version}/bin"] ) {
                def output = sh returnStdout: true, script: 'JAVA_HOME=/etc/alternatives/java mvn -PautoInstallPackage clean package'
				println output
            }
        }
    }
    catch (err) {
        
    }

	def VERSION = sh returnStdout: true, script: 'head -n20 '+workspace+'/pom.xml |grep \'<version>\'|cut -d\\> -f2|cut -d\\< -f1|head -n1'
	VERSION = VERSION.trim()
	
	withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: VMLJENKINS, usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD']]) {
		stage ("Author Bundle Deploy") {
			def PACKAGE_NAME = PACKAGE_NAME_BND+'-'+VERSION+'.jar'

			//Update Bundle in OSGI console/bundles
			def output = sh returnStdout: true, script: 'curl -vu '+env.USERNAME+':'+env.PASSWORD+' -F action=install -F bundlestartlevel=20 -F bundlefile=@"'+PACKAGE_PATH_BND+'/'+PACKAGE_NAME+'" '+AEM_AUTHOR+'/system/console/bundles'
			println output
			sleep(15)
			output = sh returnStdout: true, script: 'curl -vu '+env.USERNAME+':'+env.PASSWORD+' '+AEM_AUTHOR+'/system/console/bundles/'+AEM_BUNDLENAME+' -Faction=start'
			println output
		}
		
		stage ("Author Content Deploy") {
			def PACKAGE_NAME = PACKAGE_NAME_APP+'-'+VERSION+'.zip'
			
			//Upload
			def output = sh returnStdout: true, script: 'curl -s -u '+env.USERNAME+':'+env.PASSWORD+' -F package=@"'+PACKAGE_PATH_APP+'/'+PACKAGE_NAME+'" '+AEM_AUTHOR+'/crx/packmgr/service/.json/?cmd=upload -F force="true"'
			println output
			//install
			output = sh returnStdout: true, script: 'curl -s -u '+env.USERNAME+':'+env.PASSWORD+' -X POST '+AEM_AUTHOR+'/crx/packmgr/service/.json/etc/packages/'+PACKAGE_LOCATION+'/'+PACKAGE_NAME+'?cmd=install'
			println output
		}
		
		stage ("Publisher Bundle Deploy") {
			def PACKAGE_NAME = PACKAGE_NAME_BND+'-'+VERSION+'.jar'

			//Update Bundle in OSGI console/bundles
			def output = sh returnStdout: true, script: 'curl -vu '+env.USERNAME+':'+env.PASSWORD+' -F action=install -F bundlestartlevel=20 -F bundlefile=@"'+PACKAGE_PATH_BND+'/'+PACKAGE_NAME+'" '+AEM_PUBLISHER+'/system/console/bundles'
			println output
			sleep(15)
			output = sh returnStdout: true, script: 'curl -vu '+env.USERNAME+':'+env.PASSWORD+' '+AEM_PUBLISHER+'/system/console/bundles/'+AEM_BUNDLENAME+' -Faction=start'
			println output		
		}
		
		stage ("Publisher Content Deploy") {
			def PACKAGE_NAME = PACKAGE_NAME_APP+'-'+VERSION+'.zip'
			
			//Upload
			def output = sh returnStdout: true, script: 'curl -s -u '+env.USERNAME+':'+env.PASSWORD+' -F package=@"'+PACKAGE_PATH_APP+'/'+PACKAGE_NAME+'" '+AEM_PUBLISHER+'/crx/packmgr/service/.json/?cmd=upload -F force="true"'
			println output
			//install
			output = sh returnStdout: true, script: 'curl -s -u '+env.USERNAME+':'+env.PASSWORD+' -X POST '+AEM_PUBLISHER+'/crx/packmgr/service/.json/etc/packages/'+PACKAGE_LOCATION+'/'+PACKAGE_NAME+'?cmd=install'
			println output
		}
	}
}

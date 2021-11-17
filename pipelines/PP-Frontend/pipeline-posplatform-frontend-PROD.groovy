pipeline{
    agent { label 'jump' }

    stages{


        stage('Pull GIT'){            
            steps {
                git branch: 'master' , credentialsId: 'GIT' , url: 'https://F1XNZSH@escmstash.1dc.com/scm/pp/posplatform-parent.git'
            }
        }
     
         stage('Build and Deploy to Nexus'){
            steps {
                sh  'mvn  clean  package -Dmaven.test.skip=true'
                archiveArtifacts allowEmptyArchive: true, artifacts: 'posplatform-frontend/target/posplatform-frontend.war', followSymlinks: false, onlyIfSuccessful: true
            }
        }
     

        stage('Artifacts Delivery to PROD'){
            steps {
                sshPublisher(publishers: [sshPublisherDesc(configName: 'LKPVAP1054', transfers: [sshTransfer(cleanRemote: true, excludes: '', execCommand: '', execTimeout: 120000, flatten: false, makeEmptyDirs: false, noDefaultExcludes: false, patternSeparator: '[, ]+', remoteDirectory: '', remoteDirectorySDF: false, removePrefix: 'posplatform-frontend/target', sourceFiles: 'posplatform-frontend/target/posplatform-frontend.war')], usePromotionTimestamp: false, useWorkspaceInPromotion: false, verbose: false)])
            }
                
        }

        stage ('Support WILDFY'){
            steps {
                script {
                    def remote = [:]
                    remote.name = 'PROD'
                    remote.host = 'LKPVAP1054.1dc.com'
                    remote.allowAnyHosts = true
					
					withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'sshUser', usernameVariable: 'userName', passwordVariable: 'password']]) {
						remote.user = userName
						remote.password = password

					}

                    //ETAPA DE MANTENIMIENTO BORRAR  
                    writeFile file: 'borrarTmp.bash', text: "rm -rf /appserver/wildfly/domain/servers/LKPVAP1054/tmp/* \n sudo /etc/init.d/wildfly restart  \n sleep 2m"
                    sshScript remote: remote, script: "borrarTmp.bash"

                }
            }

        }

        stage('Deploy PROD') {        
            steps {
                script {
                    def remote = [:]
                    remote.name = 'DR'
                    remote.host = 'LKPVAP1054.1dc.com'
                    remote.allowAnyHosts = true
					
					withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'sshUser', usernameVariable: 'userName', passwordVariable: 'password']]) {
						remote.user = userName
						remote.password = password

					}
					
                    //ETAPA DE DEPLOPY                  
                    writeFile file: 'deploy.cli', text: "connect \n deploy -f /tmp/to_implement/posplatform-frontend.war"
                    sshPut remote: remote, from: 'deploy.cli', into: '/tmp/to_implement'
                    writeFile file: 'ejecusionDeploy.sh', text:"/appserver/wildfly/bin/jboss-cli.sh --controller=127.0.0.1 --file=/tmp/to_implement/deploy.cli"
                    sshScript remote: remote, script: "ejecusionDeploy.sh"

                }
            }
        }
            
            


    }
}

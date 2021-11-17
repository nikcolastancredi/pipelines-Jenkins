pipeline{
    agent { label 'Principal' }

    environment {
        Server = "lkqvap1013"
        DirectorioRemoto = "/aplicaciones/to_implement"
        ArtifactsDeployment = "posplatform-services.war"
        DirectorioArtifacts = "posplatform-services/target"
        Entorno = "UAT"
    }


    stages{


        stage('Pull GIT'){            
            steps {
                git branch: 'develop' , credentialsId: 'GIT' , url: 'https://F1XNZSH@escmstash.1dc.com/scm/posplatform-services/posplatform-services-parent.git'
            }
        }
     
         stage('Build'){
            steps {
                sh  'mvn  clean  package -Dmaven.test.skip=true'
                archiveArtifacts allowEmptyArchive: true, artifacts: "${env.DirectorioArtifacts}/${env.ArtifactsDeployment}", followSymlinks: false, onlyIfSuccessful: true
            }
        }
   

        stage('Artifacts Delivery to UAT'){
            steps {
                sshPublisher(publishers: [sshPublisherDesc(configName: "${env.Server}", transfers: [sshTransfer(cleanRemote: true, excludes: '', execCommand: '', execTimeout: 120000, flatten: false, makeEmptyDirs: false, noDefaultExcludes: false, patternSeparator: '[, ]+', remoteDirectory: '', remoteDirectorySDF: false, removePrefix: "${env.DirectorioArtifacts}", sourceFiles: "${env.DirectorioArtifacts}/${env.ArtifactsDeployment}")], usePromotionTimestamp: false, useWorkspaceInPromotion: false, verbose: false)])
            }
                
        }


        stage('Deploy UAT') {        
            parallel{
                stage('Deploy LKQVAP1013'){
                    steps {
                        script {
                            def remote = [:]
                            remote.name = "${env.Entorno}"
                            remote.host = "${env.Server}.1dc.com"
                            remote.allowAnyHosts = true

			        		withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'sshUser', usernameVariable: 'userName', passwordVariable: 'password']]) {
			        			remote.user = userName
			        			remote.password = password

			        		}

                            //ETAPA DE UNDEPLOY                  
                            writeFile file: 'undeploy.cli', text: "connect \n undeploy ${env.ArtifactsDeployment} --server-groups=grupo-ha"
                            sshPut remote: remote, from: 'undeploy.cli', into: "${env.DirectorioRemoto}"
                            writeFile file: 'ejecusionUndeploy.sh', text:"#!/bin/sh \n /appserver/wildfly/bin/jboss-cli.sh  --file=${env.DirectorioRemoto}/undeploy.cli \n exit 0"
                            sshScript remote: remote, script: "ejecusionUndeploy.sh"


                            //ETAPA  DE BORRADO DE tmp
                            writeFile file: 'borrarTmp.bash', text: "sudo /etc/init.d/wildfly stop  \n rm -rf /appserver/wildfly/domain/servers/${env.Server}/tmp/* \n sudo /etc/init.d/wildfly start  \n sleep 2m"
                            sshScript remote: remote, script: "borrarTmp.bash"

                            //ETAPA DE DEPLOY  
                            writeFile file: 'deploy.cli', text: "connect \n deploy ${env.DirectorioRemoto}/${env.ArtifactsDeployment} --server-groups=grupo-ha"
                            sshPut remote: remote, from: 'deploy.cli', into: "${env.DirectorioRemoto}"
                            writeFile file: 'ejecusionDeploy.sh', text:"/appserver/wildfly/bin/jboss-cli.sh --controller=127.0.0.1 --file=${env.DirectorioRemoto}/deploy.cli"
                            sshScript remote: remote, script: "ejecusionDeploy.sh"


                        }
                    }
                }
            }
        }
            
            


    }
}

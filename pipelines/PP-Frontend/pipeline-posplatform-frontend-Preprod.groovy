import java.text.SimpleDateFormat

pipeline{
    agent { label 'Principal' }

    environment {
        ServerMaster = "llbvap1025"
        ServerSlave = "llbvap1026"
        ServerJump  = "rkpvtl1000"
        DirectorioRemoto = "/aplicaciones/to_implement"
        ArtifactsDeployment = "posplatform-frontend.war"
        DirectorioArtifacts = "posplatform-frontend/target"
        DirectorioFileConfig = "posplatform-backend/external-conf"
        DirectorioRemotoFileConfig = "/aplicaciones/posnetplatform"
        Entorno = "PREPROD"
    }

    stages{


        stage('Pull GIT'){            
            steps {
                git branch: 'preprod' , credentialsId: 'GIT' , url: 'https://F1XNZSH@escmstash.1dc.com/scm/pp/posplatform-parent.git'
            }
        }
     
         stage('Build'){
            steps {
                sh  'mvn  clean  package -Dmaven.test.skip=true'
                archiveArtifacts allowEmptyArchive: true, artifacts: "${env.DirectorioArtifacts}/${env.ArtifactsDeployment}", followSymlinks: false, onlyIfSuccessful: true

            }
        }
   

        stage('Artifacts Delivery to Jump'){
            steps {
                sshPublisher(publishers: [sshPublisherDesc(configName: "${env.ServerJump}", transfers: [sshTransfer(cleanRemote: true, excludes: '', execCommand: '', execTimeout: 120000, flatten: false, makeEmptyDirs: false, noDefaultExcludes: false, patternSeparator: '[, ]+', remoteDirectory: '/workspace/pipeline-posplatform-frontend-Preprod', remoteDirectorySDF: false, removePrefix: "${env.DirectorioArtifacts}", sourceFiles: "${env.DirectorioArtifacts}/${env.ArtifactsDeployment}")], usePromotionTimestamp: false, useWorkspaceInPromotion: false, verbose: false)])
                // sshPublisher(publishers: [sshPublisherDesc(configName: 'rkpvtl1000', transfers: [sshTransfer(cleanRemote: false, excludes: '', execCommand: '', execTimeout: 120000, flatten: false, makeEmptyDirs: false, noDefaultExcludes: false, patternSeparator: '[, ]+', remoteDirectory: '/workspace/pipeline-posplatform-frontend-Preprod', remoteDirectorySDF: false, removePrefix: 'posplatform-backend/external-conf', sourceFiles: 'posplatform-backend/external-conf/*.properties')], usePromotionTimestamp: false, useWorkspaceInPromotion: false, verbose: false)])

            }
                
        }
        stage('Artifacts Delivery to Preprod'){
            agent { label 'jump' }
            
            steps {
                sshPublisher(publishers: [sshPublisherDesc(configName: "${env.ServerMaster}", transfers: [sshTransfer(cleanRemote: true, excludes: '', execCommand: '', execTimeout: 120000, flatten: false, makeEmptyDirs: false, noDefaultExcludes: false, patternSeparator: '[, ]+', remoteDirectory: '', remoteDirectorySDF: false, removePrefix: '', sourceFiles: "${env.ArtifactsDeployment}")], usePromotionTimestamp: false, useWorkspaceInPromotion: false, verbose: false)])            
                // sshPublisher(publishers: [sshPublisherDesc(configName: 'llbvap1025', transfers: [sshTransfer(cleanRemote: false, excludes: '', execCommand: '', execTimeout: 120000, flatten: false, makeEmptyDirs: false, noDefaultExcludes: false, patternSeparator: '[, ]+', remoteDirectory: '', remoteDirectorySDF: false, removePrefix: '', sourceFiles: 'posplatform-backend/external-conf/*.properties')], usePromotionTimestamp: false, useWorkspaceInPromotion: false, verbose: false)])
                // sshPublisher(publishers: [sshPublisherDesc(configName: 'llbvap1026', transfers: [sshTransfer(cleanRemote: false, excludes: '', execCommand: '', execTimeout: 120000, flatten: false, makeEmptyDirs: false, noDefaultExcludes: false, patternSeparator: '[, ]+', remoteDirectory: '', remoteDirectorySDF: false, removePrefix: 'posplatform-backend/external-conf', sourceFiles: 'posplatform-backend/external-conf/*.properties')], usePromotionTimestamp: false, useWorkspaceInPromotion: false, verbose: false)])

            }
        }       
            
      
        stage('Deploy PREPROD') {
            parallel{

                stage('Deploy '){
                    agent { label 'jump' }
                    steps {
                        script {
                            def remote = [:]
                            remote.name = "${env.Entorno}"
                            remote.host = "${env.ServerMaster}.1dc.com"
                            remote.allowAnyHosts = true

			        		withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'sshUser', usernameVariable: 'userName', passwordVariable: 'password']]) {
			        			remote.user = userName
			        			remote.password = password

			        		}

                            //ETAPA COPY ARCHIVOS .PROPERTIES 
                            //writeFile file: 'copyFiles.sh', text:"cp --force ${env.DirectorioRemoto}/*.properties  ${env.DirectorioRemotoFileConfig}"
                            //sshScript remote: remote, script: "copyFiles.sh"

                            //ETAPA DE UNDEPLOY                  
                            writeFile file: 'undeploy.cli', text: "connect \n undeploy ${env.ArtifactsDeployment} --server-groups=grupo-ha"
                            sshPut remote: remote, from: 'undeploy.cli', into: "${env.DirectorioRemoto}"
                            writeFile file: 'ejecusionUndeploy.sh', text:"#!/bin/sh \n /appserver/wildfly/bin/jboss-cli.sh  --file=${env.DirectorioRemoto}/undeploy.cli \n exit 0"
                            sshScript remote: remote, script: "ejecusionUndeploy.sh"


                            //ETAPA  DE BORRADO DE tmp
                            writeFile file: 'borrarTmp.bash', text: "sudo /etc/init.d/wildfly stop  \n rm -rf /appserver/wildfly/domain/servers/${env.ServerMaster}/tmp/* \n sudo /etc/init.d/wildfly start  \n sleep 2m"
                            sshScript remote: remote, script: "borrarTmp.bash"

                            //ETAPA DE DEPLOY  
                            writeFile file: 'deploy.cli', text: "connect \n deploy ${env.DirectorioRemoto}/${env.ArtifactsDeployment} --server-groups=grupo-ha"
                            sshPut remote: remote, from: 'deploy.cli', into: "${env.DirectorioRemoto}"
                            writeFile file: 'ejecusionDeploy.sh', text:"/appserver/wildfly/bin/jboss-cli.sh --controller=127.0.0.1 --file=${env.DirectorioRemoto}/deploy.cli"
                            sshScript remote: remote, script: "ejecusionDeploy.sh"


                    

                        }
                    }
                }

                // stage('Deploy LLBVAP1026'){
                //     agent { label 'jump' }
                //     steps {
                //         script {
                //             def remote = [:]
                //             remote.name = 'Preprod'
                //             remote.host = 'llbvap1026.1dc.com'
                //             remote.allowAnyHosts = true

			    //     		    withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'sshUser', usernameVariable: 'userName', passwordVariable: 'password']]) {
			    //     			    remote.user = userName
			    //     			    remote.password = password

                //             }


                //             //ETAPA COPY ARCHIVOS .PROPERTIES 
                //             writeFile file: 'copyFiles.sh', text:"cp --force /tmp/to_implement/*.properties  /aplicaciones/posnetplatform"
                //             sshScript remote: remote, script: "copyFiles.sh"

                //         }
                //     }
                // }


            }
        }

        stage("TAG AUTOMATICO") {
            steps {
                script {
                    def date = new Date()
                    def sdf = new SimpleDateFormat("yyyyMMdd")
                    def FECHA =  sdf.format(date)
                    def VERSION = sh(script: "(git log -p -1 | grep RELEASE | head -n 1 | tr -d '[[:space:]]')", returnStdout: true)                 

                    withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'GIT', usernameVariable: 'GIT_USERNAME', passwordVariable: 'GIT_PASSWORD']]) { 
                        sh "git tag ${FECHA}-'${VERSION}'-'${env.Entorno}'-${env.BUILD_NUMBER}"
                        sh ("git push https://${env.GIT_USERNAME}:'${env.GIT_PASSWORD}'@escmstash.1dc.com/scm/pp/posplatform-parent.git ${FECHA}-'${VERSION}'-'${env.Entorno}'-${env.BUILD_NUMBER}")
                    }
                }
            }
        }

    }
}

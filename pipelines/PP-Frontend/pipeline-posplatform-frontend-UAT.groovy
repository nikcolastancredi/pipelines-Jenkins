import java.text.SimpleDateFormat

pipeline{
    agent { label 'Principal' }

    environment {
        ServerMaster = "lkqvap1007"
        ServerSlave = "lkqvap1008"
        DirectorioRemoto = "/aplicaciones/to_implement"
        ArtifactsDeployment = "posplatform-frontend.war"
        DirectorioArtifacts = "posplatform-frontend/target"
        DirectorioFileConfig = "posplatform-backend/external-conf"
        DirectorioRemotoFileConfig = "/aplicaciones/posnetplatform"
        Entorno = "UAT"
    }

    stages{


        stage('Pull GIT'){            
            steps {
                git branch: 'develop' , credentialsId: 'GIT' , url: 'https://F1XNZSH@escmstash.1dc.com/scm/pp/posplatform-parent.git'
            }
        }
     
         stage('Build and Deploy to Nexus'){
            steps {
                sh  'mvn  clean  deploy -Dmaven.test.skip=true'
                archiveArtifacts allowEmptyArchive: true, artifacts: "${env.DirectorioArtifacts}/${env.ArtifactsDeployment}", followSymlinks: false, onlyIfSuccessful: true
            }
        }
        
        stage('Scan to code'){
            parallel{
                stage('Scan Fortify'){
                    steps{
                        sh 'export BUILDID="UAID-03192" \n ## ejecución del Fortify SCA \n #Clean \n $FORTIFY_HOME/bin/sourceanalyzer -b $BUILDID -clean \n #Build \n $FORTIFY_HOME/bin/sourceanalyzer -b $BUILDID $WORKSPACE/posplatform-frontend/src \n ## - Remote Translation and Remote Scan (Recommended Usage) \n $FORTIFY_HOME/bin/scancentral  -sscurl https://fortify.1dc.com/ssc -ssctoken "56a2a364-115c-4bcc-b033-29fca4e1d920" start -upload -application "UAID-03192" -application-version "1.0" -b $BUILDID -uptoken "56a2a364-115c-4bcc-b033-29fca4e1d920" -scan'
                        
                    }
                }
                // stage('Scan Sonar'){
                //     steps{
                //         sh  'mvn com.sonatype.clm:clm-maven-plugin:evaluate -Dclm.additionalScopes=test,provided,system -Dclm.applicationId=UAID-03192 -Dclm.serverUrl=https://lifecycle.1dc.com -Dclm.username=La7YBVv0 -Dclm.password=79jwNDMPslVffoYMf0aYDBzV8Ha9mIfG8chxGC9MtSSp'
                //     }

            }
        }



        stage('Artifacts Delivery to UAT'){
            steps {
                sshPublisher(publishers: [sshPublisherDesc(configName: "${env.ServerMaster}", transfers: [sshTransfer(cleanRemote: true, excludes: '', execCommand: '', execTimeout: 120000, flatten: false, makeEmptyDirs: false, noDefaultExcludes: false, patternSeparator: '[, ]+', remoteDirectory: '', remoteDirectorySDF: false, removePrefix: "${env.DirectorioArtifacts}", sourceFiles: "${env.DirectorioArtifacts}/${env.ArtifactsDeployment}")], usePromotionTimestamp: false, useWorkspaceInPromotion: false, verbose: false)])
                sshPublisher(publishers: [sshPublisherDesc(configName: "${env.ServerMaster}", transfers: [sshTransfer(cleanRemote: false, excludes: '', execCommand: '', execTimeout: 120000, flatten: false, makeEmptyDirs: false, noDefaultExcludes: false, patternSeparator: '[, ]+', remoteDirectory: '', remoteDirectorySDF: false, removePrefix: "${env.DirectorioFileConfig}", sourceFiles: "${env.DirectorioFileConfig}/*.properties")], usePromotionTimestamp: false, useWorkspaceInPromotion: false, verbose: false)])
                sshPublisher(publishers: [sshPublisherDesc(configName: "${env.ServerSlave}", transfers: [sshTransfer(cleanRemote: true, excludes: '', execCommand: '', execTimeout: 120000, flatten: false, makeEmptyDirs: false, noDefaultExcludes: false, patternSeparator: '[, ]+', remoteDirectory: '', remoteDirectorySDF: false, removePrefix: "${env.DirectorioFileConfig}", sourceFiles: "${env.DirectorioFileConfig}/*.properties")], usePromotionTimestamp: false, useWorkspaceInPromotion: false, verbose: false)])

            }
        }


        stage('Deploy UAT') {        
            parallel{
                stage('Deploy LKQVAP1007'){
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
                            writeFile file: 'copyFiles.sh', text:"cp --force ${env.DirectorioRemoto}/*.properties  ${env.DirectorioRemotoFileConfig}"
                            sshScript remote: remote, script: "copyFiles.sh"

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

                stage('Deploy LKQVAP1008'){
                    steps {
                        script {
                            def remote = [:]
                            remote.name = "${env.Entorno}"
                            remote.host = "${ServerSlave}.1dc.com"
                            remote.allowAnyHosts = true

			        		withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'sshUser', usernameVariable: 'userName', passwordVariable: 'password']]) {
			        			remote.user = userName
			        			remote.password = password

                            }


                            //ETAPA COPY ARCHIVOS .PROPERTIES 
                            writeFile file: 'copyFiles.sh', text:"cp --force ${env.DirectorioRemoto}/*.properties  ${env.DirectorioRemotoFileConfig}"
                            sshScript remote: remote, script: "copyFiles.sh"

                        }
                    }
                }



            
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
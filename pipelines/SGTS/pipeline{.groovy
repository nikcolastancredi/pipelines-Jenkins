pipeline{
    agent { label 'jump' }

     environment {
        Deploy = "/opt/conf/catalogaciones/catalogaciones/TEST/GeneXusTeam/SGTS/DEV/Deploys/*"
        Fromgam = "/tmp/to_implement/En_Carpeta_gam/*"
        Fromxsls = "/tmp/to_implement/EnDirectorio_xsls/*"
        Destgam = "/appserver/aplicaciones/sgts/gam/"
        Destxsls = "/appserver/aplicaciones/sgts/xsls/"
    }
        
    stages{
        stage('Artifacts Delivery to DEV'){
            steps{
                sh "cp -R ${env.Deploy} ${env.WORKSPACE}"
                sshPublisher(publishers: [sshPublisherDesc(configName: 'lkdvap1043', transfers: [sshTransfer(cleanRemote: true, excludes: '', execCommand: '', execTimeout: 120000, flatten: false, makeEmptyDirs: false, noDefaultExcludes: false, patternSeparator: '[, ]+', remoteDirectory: '', remoteDirectorySDF: false, removePrefix: '', sourceFiles: '*/**')], usePromotionTimestamp: false, useWorkspaceInPromotion: false, verbose: false)])

            }
        }
    
    
        stage("Copy to Files"){
            parallel{
                stage("ARCHIVOS GAM"){
                    steps{
                        script {
                            def remote = [:]
                            remote.name = 'DEV'
                            remote.host = 'lkdvap1043.1dc.com'
                            remote.allowAnyHosts = true
    				
    				        withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'sshUser', usernameVariable: 'userName', passwordVariable: 'password']]) {
    					        remote.user = userName
    					        remote.password = password
    
    				        }

                            //MOVIMIENTO DE ARCHIVOS GAM
                            writeFile file: 'moveFilesGam.sh', text: "#!/bin/sh \n cp -R ${env.Fromgam} ${env.Destgam}  \n exit 0"
                            sshScript remote: remote, script: "moveFilesGam.sh"
                        }
                    }

                } 
                
                stage("ARCHIVOS XSLS"){
                    steps{
                        script {
                            def remote = [:]
                            remote.name = 'DEV'
                            remote.host = 'lkdvap1043.1dc.com'
                            remote.allowAnyHosts = true
    				
    				        withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'sshUser', usernameVariable: 'userName', passwordVariable: 'password']]) {
    					        remote.user = userName
    					        remote.password = password
    
    				        }

                            //MOVIMIENTO DE ARCHIVOS XSLS
                            writeFile file: 'moveFilesXSLS.sh', text: "#!/bin/sh \n cp -R ${env.Fromxsls} ${env.Destxsls} \n exit 0"
                            sshScript remote: remote, script: "moveFilesXSLS.sh"
                        
                        }
                    }
                }


            }
        }
        
        stage('Deploy DEV'){     
            steps{
                script{
                    def remote = [:]
                    remote.name = 'dev'
                    remote.host = 'lkdvap1043.1dc.com'
                    remote.allowAnyHosts = true
    				
    				withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'sshUser', usernameVariable: 'userName', passwordVariable: 'password']]) {
    					remote.user = userName
    					remote.password = password
    
    				}

                    //ETAPA DE DEPLOPY                  
                    writeFile file: 'deploy.cli', text: "connect \n deploy -f /tmp/to_implement/sgts.war"
                    sshPut remote: remote, from: 'deploy.cli', into: '/tmp/to_implement'
    
                       
                    writeFile file: 'ejecusionDeploy.sh', text:"/appserver/wildfly/bin/jboss-cli.sh  --file=/tmp/to_implement/deploy.cli"
                    sshScript remote: remote, script: "ejecusionDeploy.sh"
                        
                    //ETAPA DE CLEAN ARTEFACTOS_ JUMP 
                    sh 'sudo  /usr/bin/rm -rf  /opt/conf/catalogaciones/catalogaciones/TEST/GeneXusTeam/SGTS/Dev/Deploys/*'

                    //URL DE PRUEBA
                    //https://devsgts.1dc.com/sgts/servlet/com.fiserv.geologin
                       
                }
            }
        }
       


       
    }
}
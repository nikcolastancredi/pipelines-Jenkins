import java.text.SimpleDateFormat

pipeline{
    agent { label 'Principal' }


    stages{


        stage('Pull GIT'){            
            steps {
                git branch: 'develop' , credentialsId: 'GIT' , url: 'https://F1XNZSH@escmstash.1dc.com/scm/pp/posplatform-parent.git'
            }
        }
     
         stage('Build and Deploy to Nexus'){
            steps {
                sh  'mvn  clean  package -Dmaven.test.skip=true'                              
                archiveArtifacts allowEmptyArchive: true, artifacts: 'posplatform-frontend/target/posplatform-frontend.war', followSymlinks: false, onlyIfSuccessful: true
            }
        }
       
        stage('Artifacts Delivery to UAT'){
            steps {
                sshPublisher(publishers: [sshPublisherDesc(configName: 'lkqvap1007', transfers: [sshTransfer(cleanRemote: true, excludes: '', execCommand: '', execTimeout: 120000, flatten: false, makeEmptyDirs: false, noDefaultExcludes: false, patternSeparator: '[, ]+', remoteDirectory: '', remoteDirectorySDF: false, removePrefix: 'posplatform-frontend/target', sourceFiles: 'posplatform-frontend/target/posplatform-frontend.war')], usePromotionTimestamp: false, useWorkspaceInPromotion: false, verbose: false)])
                
            }
        }
        
        stage("TAG AUTOMATICO") {
            steps {
                script {
                    def date = new Date()
                    def sdf = new SimpleDateFormat("yyyyMMdd")
                    def FECHA =  sdf.format(date)
                    def VERSION = sh(script: "(git log -p -1 | grep RELEASE | tr -d '[[:space:]]')", returnStdout: true)                 

                    withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'GIT', usernameVariable: 'GIT_USERNAME', passwordVariable: 'GIT_PASSWORD']]) { 
                        sh "git tag ${FECHA}-'${VERSION}'-UAT-${env.BUILD_NUMBER}"
                        sh ("git push https://${env.GIT_USERNAME}:'${env.GIT_PASSWORD}'@escmstash.1dc.com/scm/pp/posplatform-parent.git ${FECHA}-'${VERSION}'-UAT-${env.BUILD_NUMBER}")
                    }
                }
            }
        }


            
    }
}

workspace {

    model {
        participant = person "Participant" "A participant in the study."
        researcher = person "Researcher" "A researcher running the study."
        
        
        facepsySystem = softwareSystem "FacePsy System" "Oprtusistic facial behavior data collection system." {
                facepsyApp = container "FacePsy App" "" "Android" {
                    hiddencam = component "Oprtusistic background camera sensing" "" "Camera API"
                    firebaseConn = component "Firebase Connector" "" "Firebase SDK"
                    appUsageEventLoggger = component "Usage Event Listner" "" "UsageStat API"
                    cogGames = component "Cognitive Tasks and Survey" "" ""
                    notification = component "Daily Notification" "" "Firebase Cloud Messaging"
                    behaviorSensing = component "Facial Behavior Sensing" "" "TensorFlow Lite, ML Kit"
                    # survyeWebview = component "Survey View" "" "Web View"
                }
                dashboardApp = container "Compliance Dashboard" "" "Python Plotly"

        }
        
        qualtricsSystem = softwareSystem "Qualtrics" "An online survey platform."
        firebaseSystem = softwareSystem "Google Firebase" "An app development platform." {
            fcm = container "Firebase Cloud Messaging" "" "Cloud solution for messages and notifications."
        }
        
        bigquerySystem = softwareSystem "Google Big Query" "Data warehouse."
        
        participant -> facepsySystem "Uses"
        participant -> facepsyApp "Uses"
        
        researcher -> facepsySystem "Uses"
        researcher -> dashboardApp "Uses"
        
        facepsySystem -> qualtricsSystem "FacePsy redirects survey request to Qualtrics"
        facepsySystem -> firebaseSystem "App Backend"
        facepsySystem -> bigquerySystem "Analytics Backend"
        
        dashboardApp -> bigquerySystem "Visualize data"
        dashboardApp -> qualtricsSystem "Survey data"
        facepsyApp -> firebaseSystem "App Backend"
        firebaseSystem -> bigquerySystem "Sync"
        facepsyApp -> qualtricsSystem "User takes survey"
        
        firebaseConn -> firebaseSystem "API"
        
        notification -> fcm "Cloud Messaging API"
        
        appUsageEventLoggger -> hiddencam "Triggers Data Collection"
        hiddencam -> behaviorSensing "Schedule background processing"
        behaviorSensing -> firebaseConn "Store to Firebase"
        
        cogGames -> firebaseConn "Store to Firebase"
        cogGames -> qualtricsSystem "Uses"
        
        

    }

    views {
        systemContext facepsySystem "SystemContext" {
            include *
            autoLayout lr
        }

        container facepsySystem "SystemContainer" "Facial behavior sensing mobile application" {
            include *
            autoLayout lr
        }
        
        component facepsyApp "SystemComponent" {
            include *
            autoLayout lr
        }
        
        theme default
    }
    
}
import firebase_admin
from firebase_admin import credentials, firestore
import json

# Load the new Firebase project credentials
cred = credentials.Certificate("./cred/filename.json")  # Use the new project's key
firebase_admin.initialize_app(cred)

db = firestore.client()

def upload_collection(collection_name, json_file):
    # Load the downloaded data
    with open(json_file, "r") as f:
        data = json.load(f)
    
    # Upload documents to Firestore
    for doc_id, doc_data in data.items():
        db.collection(collection_name).document(doc_id).set(doc_data)
    
    print(f"Successfully uploaded {len(data)} documents to {collection_name}")

# Example: Upload "acollection" to the new Firestore
upload_collection("config", "config.json")

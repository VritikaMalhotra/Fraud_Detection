"""
ML Fraud Detection Service
Serves XGBoost model predictions via REST API
"""
import os
import pickle
import json
from flask import Flask, request, jsonify
from flask_cors import CORS
import logging

app = Flask(__name__)
CORS(app)
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# Load model and metadata
MODEL_PATH = os.getenv('MODEL_PATH', '/app/models/fraud_model_xgb.pkl')
META_PATH = os.getenv('META_PATH', '/app/models/model_meta.json')

model = None
model_meta = None

def load_model():
    global model, model_meta
    try:
        # Handle relative paths
        import os
        model_path = os.path.abspath(MODEL_PATH) if not os.path.isabs(MODEL_PATH) else MODEL_PATH
        meta_path = os.path.abspath(META_PATH) if not os.path.isabs(META_PATH) else META_PATH
        
        logger.info(f"Loading model from {model_path}")
        with open(model_path, 'rb') as f:
            model = pickle.load(f)
        logger.info("Model loaded successfully")
        
        logger.info(f"Loading metadata from {meta_path}")
        with open(meta_path, 'r') as f:
            model_meta = json.load(f)
        logger.info(f"Metadata loaded: {model_meta['model_version']}")
        return True
    except Exception as e:
        logger.error(f"Failed to load model: {e}", exc_info=True)
        return False

@app.route('/health', methods=['GET'])
def health():
    return jsonify({
        'status': 'UP' if model is not None else 'DOWN',
        'model_loaded': model is not None,
        'model_version': model_meta.get('model_version') if model_meta else None
    })

@app.route('/predict', methods=['POST'])
def predict():
    if model is None:
        return jsonify({'error': 'Model not loaded'}), 503
    
    try:
        data = request.get_json()
        features = data.get('features', [])
        
        if len(features) != len(model_meta['features']):
            return jsonify({
                'error': f'Expected {len(model_meta["features"])} features, got {len(features)}',
                'expected_features': model_meta['features']
            }), 400
        
        # Convert to numpy array and predict
        import numpy as np
        feature_array = np.array([features], dtype=float)
        
        # Get prediction probabilities
        probabilities = model.predict_proba(feature_array)[0]
        
        # For binary classification: [not_fraud_prob, fraud_prob]
        # For multi-class: [ALLOW_prob, REVIEW_prob, BLOCK_prob]
        if len(probabilities) == 2:
            fraud_probability = probabilities[1]
        else:
            # Multi-class: fraud = REVIEW + BLOCK
            fraud_probability = probabilities[1] + probabilities[2] if len(probabilities) > 2 else probabilities[1]
        
        # Get prediction class
        prediction = model.predict(feature_array)[0]
        
        return jsonify({
            'fraud_probability': float(fraud_probability),
            'prediction': int(prediction),
            'probabilities': [float(p) for p in probabilities],
            'model_version': model_meta['model_version']
        })
    except Exception as e:
        logger.error(f"Prediction error: {e}", exc_info=True)
        return jsonify({'error': str(e)}), 500

@app.route('/features', methods=['GET'])
def get_features():
    """Return expected feature names and order"""
    if model_meta is None:
        return jsonify({'error': 'Metadata not loaded'}), 503
    return jsonify({
        'features': model_meta['features'],
        'model_version': model_meta['model_version'],
        'block_threshold': model_meta.get('block_threshold'),
        'roc_auc': model_meta.get('roc_auc'),
        'pr_auc': model_meta.get('pr_auc')
    })

if __name__ == '__main__':
    if load_model():
        port = int(os.getenv('PORT', 8084))
        logger.info(f"Starting ML service on port {port}")
        app.run(host='0.0.0.0', port=port, debug=False)
    else:
        logger.error("Failed to load model. Exiting.")
        exit(1)


FROM python:3.11-slim

WORKDIR /app

# Copy requirements and install dependencies
COPY services/ml-service/requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copy model files
COPY services/ml-service/models/ /app/models/

# Copy application
COPY services/ml-service/app.py .

# Expose port
EXPOSE 8084

# Run application
CMD ["python", "app.py"]

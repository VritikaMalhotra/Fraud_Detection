FROM python:3.11-slim

WORKDIR /app

# Copy requirements and install dependencies
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copy model files
COPY models/ /app/models/

# Copy application
COPY app.py .

# Expose port
EXPOSE 8084

# Run application
CMD ["python", "app.py"]


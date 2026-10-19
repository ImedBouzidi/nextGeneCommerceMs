FROM python:3.11-slim
WORKDIR /app
# Copy entire context first so builds don't fail if requirements.txt is absent
COPY . /app
# Install requirements only if the file exists
RUN if [ -f /app/requirements.txt ]; then pip install --no-cache-dir -r /app/requirements.txt; else echo "No requirements.txt, skipping pip install"; fi
ENV PYTHONUNBUFFERED=1
EXPOSE 5000
CMD ["python","app.py"]

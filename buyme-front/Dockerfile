FROM node:18-alpine

WORKDIR /app

# Install dependencies
COPY package*.json ./
RUN npm install

# Copy project files
COPY . .

# Copy start script
COPY start.sh /start.sh
RUN chmod +x /start.sh

EXPOSE 4200

# Run start script
CMD ["/start.sh"]

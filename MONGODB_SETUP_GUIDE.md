# MongoDB Conversation Memory Setup Guide

## 🚀 Quick Start

The conversation memory feature now uses **MongoDB** for persistence (no SQL database needed).

### Prerequisites
- MongoDB running locally or remotely
- Spring Data MongoDB in pom.xml (already configured)

---

## 1️⃣ Configure MongoDB Connection

Edit `application.properties`:

```properties
# Option A: Using MongoDB URI
spring.data.mongodb.uri=mongodb://localhost:27017/izinga

# Option B: Using individual properties
spring.data.mongodb.host=localhost
spring.data.mongodb.port=27017
spring.data.mongodb.database=izinga
spring.data.mongodb.username=  # if needed
spring.data.mongodb.password=  # if needed

# Enable AI agent
ai.agent.enabled=true
openai.api.key=sk-...
ai.agent.model=gpt-4.1-mini
```

---

## 2️⃣ Verify MongoDB is Running

```bash
# Start MongoDB (if not already running)
mongosh  # should connect successfully

# Or using MongoDB Docker
docker run -d -p 27017:27017 --name mongodb mongo:latest
```

---

## 3️⃣ Run Tests

```bash
# Test conversation service with MongoDB
mvn test -Dtest=ConversationHistoryServiceTest

# Test AI agent with conversation memory
mvn test -Dtest=AiCustomerServiceAgentTest
```

---

## 4️⃣ Deploy

```bash
# Build
mvn clean package

# Run (MongoDB will auto-create collection on first insert)
java -jar target/izinga-messaging-1.3.0.jar
```

---

## 📊 Viewing Data in MongoDB

### Connect to MongoDB
```bash
mongosh mongodb://localhost:27017/izinga
```

### View Conversation Collections
```javascript
// See all driver conversations
db.ai_conversation_histories.find().pretty()

// See a specific driver
db.ai_conversation_histories.findOne({ driverPhoneNumber: "+27812345678" })

// Count active conversations
db.ai_conversation_histories.countDocuments({ archived: false })

// View recent conversations
db.ai_conversation_histories
  .find({ archived: false })
  .sort({ lastMessageAt: -1 })
  .limit(10)
  .pretty()
```

### View Messages for a Conversation
```javascript
// Get all messages from a driver
db.ai_conversation_histories.findOne(
  { driverPhoneNumber: "+27812345678" },
  { messages: 1 }
)

// Get just the last 5 messages
db.ai_conversation_histories.aggregate([
  { $match: { driverPhoneNumber: "+27812345678" } },
  { $project: { messages: { $slice: ["$messages", -5] } } }
])
```

---

## 🧹 Manual Data Management

### Archive a Conversation
```javascript
db.ai_conversation_histories.updateOne(
  { driverPhoneNumber: "+27812345678" },
  { $set: { archived: true } }
)
```

### Delete All Conversations for a Driver (GDPR)
```javascript
db.ai_conversation_histories.deleteOne({ driverPhoneNumber: "+27812345678" })
```

### Clear Old Conversations
```javascript
const thirtyDaysAgo = new Date(Date.now() - 30 * 24 * 60 * 60 * 1000);
db.ai_conversation_histories.updateMany(
  { lastAccessedAt: { $lt: thirtyDaysAgo } },
  { $set: { archived: true } }
)
```

---

## 📈 Performance Optimization

### Create Indexes
```javascript
// Unique index on phone number (auto-created)
db.ai_conversation_histories.createIndex(
  { driverPhoneNumber: 1 },
  { unique: true }
)

// Index for cleanup queries
db.ai_conversation_histories.createIndex({ lastAccessedAt: 1 })

// TTL index to auto-delete after 30 days
db.ai_conversation_histories.createIndex(
  { lastAccessedAt: 1 },
  { expireAfterSeconds: 2592000 }  // 30 days
)
```

### Check Collection Size
```javascript
db.ai_conversation_histories.stats()
```

---

## 🆘 Troubleshooting

### "Cannot connect to MongoDB"
```bash
# Check if MongoDB is running
ps aux | grep mongod

# Start MongoDB
mongosh
```

### "Collection doesn't exist"
Don't worry! MongoDB auto-creates `ai_conversation_histories` on first insert.

### "Scheduled cleanup not running"
Make sure `@EnableScheduling` is on your main Spring Boot application:

```java
@SpringBootApplication
@EnableScheduling  // Add this
public class IzingaMessagingApplication {
    public static void main(String[] args) {
        SpringApplication.run(IzingaMessagingApplication.class, args);
    }
}
```

---

## 🔐 Security Best Practices

### Enable MongoDB Authentication
```javascript
// In MongoDB
db.createUser({
  user: "izinga_user",
  pwd: "strong-password",
  roles: ["readWrite"]
})
```

Then in `application.properties`:
```properties
spring.data.mongodb.uri=mongodb://izinga_user:strong-password@localhost:27017/izinga
```

### Encrypt Sensitive Data
Consider encrypting the AI responses in MongoDB if they contain personal info:
```java
// Before saving
response = encryptionService.encrypt(response);
conversationHistory.addAssistantMessage(response);
```

---

## 📊 Monitoring Queries

### Daily Active Drivers
```javascript
db.ai_conversation_histories.countDocuments({
  archived: false,
  lastAccessedAt: { $gte: new Date(new Date().setDate(new Date().getDate() - 1)) }
})
```

### Average Messages per Conversation
```javascript
db.ai_conversation_histories.aggregate([
  { $group: { 
      _id: null, 
      avgMessages: { $avg: { $size: "$messages" } }
    } 
  }
])
```

### Top Drivers by Message Count
```javascript
db.ai_conversation_histories.aggregate([
  { $project: { 
      driverPhoneNumber: 1, 
      messageCount: { $size: "$messages" } 
    } 
  },
  { $sort: { messageCount: -1 } },
  { $limit: 10 }
])
```

---

## ✅ Summary

- ✅ MongoDB stores conversation history
- ✅ Auto-creates collection on first insert
- ✅ Unique index on driver phone number
- ✅ Automatic cleanup after 30 days
- ✅ Full conversation context in OpenAI calls
- ✅ Ready for production!


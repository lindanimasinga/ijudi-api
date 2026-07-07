// ---------------------------------------------------------------------------
// insert-ambassador-user-config.mongo.js
//
// Fallback: inserts the Ambassador UserConfig document directly into MongoDB
// when the API is not reachable (e.g. during CI bootstrap or cold-start).
//
// Usage (mongosh):
//   mongosh "mongodb://localhost:27017/ijudi" insert-ambassador-user-config.mongo.js
//
// Replace the connection string with your Atlas URI or environment-specific URI.
// ---------------------------------------------------------------------------

const collection = db.getCollection("userTypeConfig");

const existing = collection.findOne({ _id: "ambassador" });
if (existing) {
    print("Ambassador UserConfig already exists — skipping insert.");
    printjson(existing);
} else {
    const doc = {
        _id: "ambassador",
        name: "ambassador",
        label: "iZinga Ambassador",
        userRole: "AMBASSADOR",
        mandatoryFields: [
            { name: "idNumber",          label: "ID Number",           dataType: "STRING" },
            { name: "bankAccountNumber", label: "Bank Account Number", dataType: "STRING" },
            { name: "bankName",          label: "Bank Name",           dataType: "STRING" },
            { name: "bankAccountType",   label: "Bank Account Type",   dataType: "STRING" }
        ],
        optionalFields: []
    };

    const result = collection.insertOne(doc);
    print("Inserted with id: " + result.insertedId);
    printjson(doc);
}

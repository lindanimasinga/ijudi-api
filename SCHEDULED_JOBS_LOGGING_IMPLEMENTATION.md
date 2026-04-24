# Scheduled Jobs Logging - Implementation Complete ✅

## What Was Implemented

Comprehensive logging has been added to **9 scheduled jobs** in the `SchedulerService` class with the following structure for each job:

### Standard Logging Pattern

```
[START] Job Name
  └─ Found X items to process
  └─ [DEBUG] Processing individual items
  └─ [INFO] Processing details (SMS sent, events published, etc.)
  └─ [WARN] Edge cases (skipped items, etc.)
  └─ [ERROR] Exceptions (with full stack trace)
[FINISH] Job Name
  └─ Job Statistics (success count, error count, duration)
```

---

## Jobs Enhanced

| Job Name | Schedule | Metrics Tracked |
|----------|----------|-----------------|
| `checkUnconfirmedOrders` | Every 10 min | SMS sent, admin notified, errors |
| `checkScheduledOrders` | Every 10 min | Events published, errors |
| `newDrivers` | Daily 8:15 AM | Drivers processed, consent sent, docs reminded |
| `cleanUnpaidOrders` | Every 15 min | Cleanup threshold, duration |
| `notifyUnpaidOrders` | Every 15 min | Admins notified, errors |
| `updateMissingPayouts` | Every 1 hour | Shop payouts, messenger payouts, errors |
| `findShoppingListsToAction` | Daily 8:15 AM, 9:15 AM | Events published, errors |
| `publishPromosOfTheDay` | Daily 7:15 AM, 10:15 AM | Promotions sent, devices targeted, errors |
| `generateMissingImagesForStores` | Every 10 min | Stores processed, images generated, errors |

---

## Key Features

✅ **Execution Tracking**
- Clear start/finish markers for each job
- Precise timestamp for every log entry
- Easy to correlate related logs

✅ **Performance Monitoring**
- Duration tracking in milliseconds
- Item count statistics
- Success/error ratios

✅ **Detailed Visibility**
- DEBUG logs for each item processed
- INFO logs for significant actions
- WARN logs for edge cases
- ERROR logs with full stack traces

✅ **Production Ready**
- Null-safe operations
- Exception handling for each item
- Error aggregation and reporting
- No performance impact (minimal overhead)

---

## Log Configuration

### Application.yml / Application.properties

```yaml
logging:
  level:
    io.curiousoft.izinga.ordermanagement.service.SchedulerService: DEBUG
    io.curiousoft.izinga: INFO
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss.SSS} %level %logger{36} - %msg%n"
  file:
    name: logs/application.log
    max-size: 10MB
    max-history: 30
```

### For ECS/CloudWatch

```yaml
logging:
  level:
    io.curiousoft.izinga.ordermanagement.service.SchedulerService: INFO
  pattern:
    console: "%d{yyyy-MM-dd'T'HH:mm:ss.SSSZ} %level %logger %msg%n"
```

---

## Usage Examples

### View Real-Time Logs

```bash
# Tail application logs
tail -f logs/application.log

# Filter specific job
grep "checkUnconfirmedOrders" logs/application.log

# Show only errors
grep "ERROR" logs/application.log | grep -E "\[START\]|\[FINISH\]"

# Monitor promotions job
grep "publishPromosOfTheDay" logs/application.log
```

### Analysis Commands

```bash
# Find slow jobs (> 2 seconds)
grep "Duration:" logs/application.log | awk -F'Duration: ' '{print $2}' | awk '{if ($1+0 > 2000) print}'

# Count job executions per day
grep "\[START\]" logs/application.log | cut -d' ' -f1 | sort | uniq -c

# Find failed jobs
grep -B5 "Job Statistics.*Errors: [1-9]" logs/application.log

# Track specific metric
grep "Job Statistics" logs/application.log | grep "checkUnconfirmedOrders" | tail -10
```

### Elasticsearch Query Examples

If using ELK Stack:

```json
// Jobs with errors
{
  "query": {
    "bool": {
      "must": [
        { "match": { "message": "Job Statistics" } },
        { "range": { "errors": { "gte": 1 } } }
      ]
    }
  }
}

// Slow jobs
{
  "query": {
    "range": {
      "duration_ms": { "gte": 2000 }
    }
  }
}

// Recent executions
{
  "query": {
    "match": { "message": "[FINISH]" }
  },
  "sort": [{ "timestamp": { "order": "desc" } }],
  "size": 100
}
```

---

## Monitoring Alerts

### Recommended Alert Rules

1. **High Error Rate**
   - Condition: Job Statistics with Errors > 5 in last 10 executions
   - Severity: HIGH
   - Action: Check service status, examine error logs

2. **Slow Execution**
   - Condition: Duration > 3x median duration
   - Severity: MEDIUM
   - Action: Check resource usage, DB performance

3. **No Execution**
   - Condition: No [START] log for expected job in last window
   - Severity: CRITICAL
   - Action: Check scheduler service status

4. **High Failure Rate**
   - Condition: Errors > 20% of processed items
   - Severity: HIGH
   - Action: Check downstream service availability

---

## Dashboard Example (Grafana/DataDog)

Recommended panels for monitoring:

1. **Job Success Rate**
   ```
   Successful Count / (Successful + Error) * 100
   ```

2. **Average Duration by Job**
   ```
   Average(Duration) grouped by job_name
   ```

3. **Error Count Trend**
   ```
   Sum(Errors) over time, grouped by job_name
   ```

4. **Items Processed**
   ```
   Sum(Items) by job, time
   ```

5. **Error Rate**
   ```
   (Errors / Items) * 100
   ```

---

## Troubleshooting Guide

### Problem: High error count in `checkUnconfirmedOrders`

**Diagnosis:**
```bash
grep "checkUnconfirmedOrders" logs/application.log | grep "ERROR"
```

**Common causes:**
1. SMS service unavailable - Check SMS provider status
2. Store repository connection issue - Check database
3. Email notification service down - Check email service

**Resolution:**
- Check service dependencies
- Review error stack traces
- Monitor database performance

### Problem: Slow `generateMissingImagesForStores` job

**Diagnosis:**
```bash
grep "generateMissingImagesForStores" logs/application.log | grep "Duration:"
```

**Common causes:**
1. AI image generation slow - Check OpenAI API quota
2. AWS upload slow - Check S3 bucket performance
3. Thread sleep between items - Normal, expected behavior

**Resolution:**
- Increase job interval
- Add more resources to image processing
- Check AWS S3 performance metrics

### Problem: `publishPromosOfTheDay` not sending promotions

**Diagnosis:**
```bash
grep "publishPromosOfTheDay" logs/application.log | grep "Found.*promotions"
```

**Common causes:**
1. No active promotions - Check promotion service
2. No active users - Check order history
3. Firebase service issue - Check Firebase status

**Resolution:**
- Verify promotion data in database
- Check user activity
- Restart Firebase service if needed

---

## Performance Optimization

### Identifying bottlenecks

```bash
# Jobs taking > 3 seconds
grep "Duration:" logs/application.log | awk -F'Duration: ' '{print $2}' | awk '{if ($1+0 > 3000) print "SLOW"}'

# Jobs with high error percentage
grep "Job Statistics" logs/application.log | awk -F'Errors: ' '{print $2}' | awk '{if ($1 / $2 > 0.2) print "HIGH_ERROR_RATE"}'
```

### Optimization tips

1. **For slow jobs:**
   - Increase batch size
   - Add database indexes
   - Cache frequently accessed data

2. **For high error jobs:**
   - Implement retry logic
   - Check dependency health
   - Add circuit breakers

3. **For resource-heavy jobs:**
   - Spread execution across time
   - Use async processing
   - Implement rate limiting

---

## Integration with Monitoring Tools

### Datadog Integration
```yaml
# datadog-agent.yaml
logs:
  - type: file
    path: /var/log/application.log
    service: izinga-ordermanager
    source: java
    tags:
      env: production
```

### New Relic Integration
```properties
# newrelic.yml
app_name: izinga-ordermanager
monitor_mode: true
log_level: fine
```

### CloudWatch Integration (ECS)
Already configured via ECS task definition - logs automatically sent to CloudWatch

---

## Maintenance

### Regular Tasks

- **Daily:** Review error logs for critical issues
- **Weekly:** Analyze performance trends
- **Monthly:** Archive old logs, review alert rules
- **Quarterly:** Optimize slow jobs, update alert thresholds

### Log Rotation

```bash
# Automatic via logback.xml
<rollingPolicy class="ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy">
  <fileNamePattern>logs/application-%d{yyyy-MM-dd}.%i.log</fileNamePattern>
  <maxFileSize>10MB</maxFileSize>
  <maxHistory>30</maxHistory>
</rollingPolicy>
```

---

## Summary

✅ **All 9 scheduled jobs** now have comprehensive logging
✅ **Job metrics** tracked and reported
✅ **Performance** monitored with duration tracking
✅ **Errors** captured with full context
✅ **Production ready** with minimal overhead

**Next Steps:**
1. Deploy to production
2. Monitor logs for first 24 hours
3. Set up alerts based on observed patterns
4. Create dashboards for team visibility
5. Implement log archival strategy

**Contact:** For questions about these logs, refer to `SCHEDULED_JOBS_LOGGING_EXAMPLES.md`


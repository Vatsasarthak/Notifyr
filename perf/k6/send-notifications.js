// Load test: POST /notifications/send at a constant arrival rate, then wait until every accepted
// notification has been delivered (mock provider). End-to-end delivery latency is computed afterwards from
// the database by perf/run-load-test.ps1 (exact sent_at - created_at percentiles for this run's user).
//
// Run through perf/run-load-test.ps1 (or see perf/README.md). Inputs (environment variables):
//   BASE_URL        API base URL                      (default http://app:8080 - inside the compose network)
//   API_KEY         value of X-API-KEY                (required)
//   AUTH_TOKEN      JWT of the run's API_CLIENT user  (required; created by run-load-test.ps1)
//   LOADTEST_USER   username of that user             (required; used for Idempotency-Keys and drain checks)
//   RATE            requests per second               (default 100)
//   DURATION        constant-rate phase duration      (default 60s)
//   MAX_VUS         upper bound of virtual users      (default 200)
import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://app:8080';
const API_KEY = __ENV.API_KEY;
// The token is read from the environment and must never be returned from setup(): k6 writes setup()'s return
// value verbatim into the --summary-export file ("setup_data"), which is kept in perf/results.
const AUTH_TOKEN = __ENV.AUTH_TOKEN;
const LOADTEST_USER = __ENV.LOADTEST_USER;
const RATE = parseInt(__ENV.RATE || '100', 10);
const DURATION = __ENV.DURATION || '60s';
const MAX_VUS = parseInt(__ENV.MAX_VUS || '200', 10);

export const options = {
    scenarios: {
        send: {
            executor: 'constant-arrival-rate',
            rate: RATE,
            timeUnit: '1s',
            duration: DURATION,
            preAllocatedVUs: Math.min(MAX_VUS, Math.max(10, RATE)),
            maxVUs: MAX_VUS,
        },
    },
    // reported targets, not guarantees: the run "fails" visibly if they are missed
    thresholds: {
        http_req_failed: ['rate<0.01'],
        'http_req_duration{name:send}': ['p(95)<500'],
    },
    summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
    teardownTimeout: '10m',
};

function apiHeaders(extra) {
    return Object.assign({
        'Content-Type': 'application/json',
        'X-API-KEY': API_KEY,
        Authorization: `Bearer ${AUTH_TOKEN}`,
    }, extra || {});
}

export function setup() {
    if (!API_KEY || !AUTH_TOKEN || !LOADTEST_USER) {
        throw new Error('API_KEY, AUTH_TOKEN and LOADTEST_USER are required - run through perf/run-load-test.ps1');
    }
    // fail before the load phase if the token is not accepted (status only - the response is not logged)
    const res = http.get(`${BASE_URL}/notifications?size=1`, { headers: apiHeaders(), tags: { name: 'auth-check' } });
    if (res.status !== 200) {
        throw new Error(`the load-test user's token was rejected: HTTP ${res.status}`);
    }
    // only non-secret values: this object ends up in the summary export
    return { username: LOADTEST_USER, startedAt: Date.now() };
}

export default function (data) {
    const payload = JSON.stringify({
        recipientEmail: `user${__VU}-${__ITER}@example.com`,
        subject: 'Load test',
        message: `Load test message from VU ${__VU} iteration ${__ITER}`,
    });
    const res = http.post(`${BASE_URL}/notifications/send`, payload, {
        headers: apiHeaders({ 'Idempotency-Key': `${data.username}-${__VU}-${__ITER}` }),
        tags: { name: 'send' },
    });
    check(res, { 'status is 202': (r) => r.status === 202 });
}

function countByStatus(data, status) {
    const res = http.get(`${BASE_URL}/notifications?status=${status}&size=1`,
        { headers: apiHeaders(), tags: { name: 'drain-check' } });
    return res.status === 200 ? res.json('totalElements') : -1;
}

// Waits for the delivery pipeline to drain, then reports delivery-side counts.
export function teardown(data) {
    const drainStart = Date.now();
    let pending = -1;
    while (Date.now() - drainStart < 9 * 60 * 1000) {
        pending = countByStatus(data, 'QUEUED') + countByStatus(data, 'PROCESSING');
        if (pending === 0) {
            break;
        }
        sleep(1);
    }
    const drainedAt = Date.now();
    const delivered = countByStatus(data, 'SIMULATED');
    const failed = countByStatus(data, 'FAILED');

    const delivery = {
        user: data.username,
        acceptedByApiThenDelivered: delivered,
        failed,
        stillPending: pending,
        secondsFromStartUntilDrained: (drainedAt - data.startedAt) / 1000,
        drainSecondsAfterLoadEnded: (drainedAt - drainStart) / 1000,
        deliveryThroughputPerSec: delivered / ((drainedAt - data.startedAt) / 1000),
    };
    console.log(`DELIVERY_RESULTS ${JSON.stringify(delivery)}`);
}

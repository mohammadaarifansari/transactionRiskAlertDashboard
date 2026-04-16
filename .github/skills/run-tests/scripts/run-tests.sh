#!/usr/bin/env bash
# run-tests.sh
# Usage: run-tests.sh <scope> [coverage]
#   scope    : frontend | backend | all  (default: all)
#   coverage : optional flag to include coverage report
#
# Exit codes:
#   0 - all tests passed
#   1 - one or more tests failed or environment error occurred

set -euo pipefail

SCOPE="${1:-all}"
COVERAGE="${2:-}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../../../.." && pwd)"

# Directories — update these when project scaffolding is added
FRONTEND_DIR="${REPO_ROOT}/frontend"
BACKEND_DIR="${REPO_ROOT}/backend"

TOTAL=0
PASSED=0
FAILED=0
SKIPPED=0
FAILING_TESTS=""
COVERAGE_OUTPUT=""
EXIT_CODE=0

# ─── Helpers ────────────────────────────────────────────────────────────────

require_tool() {
    local tool="$1"
    if ! command -v "$tool" &>/dev/null; then
        echo "ERROR: Required tool not found: $tool" >&2
        echo "       Install $tool and ensure it is on PATH before running tests." >&2
        exit 1
    fi
}

dir_exists_or_warn() {
    local dir="$1"
    local label="$2"
    if [[ ! -d "$dir" ]]; then
        echo "WARN: $label directory not found at $dir — skipping." >&2
        return 1
    fi
    return 0
}

# ─── Frontend (Angular / Karma) ──────────────────────────────────────────────

run_frontend() {
    if ! dir_exists_or_warn "$FRONTEND_DIR" "Frontend"; then
        return
    fi

    require_tool node
    require_tool npx

    echo "[frontend] Running Angular unit tests..." >&2

    local ng_args="--watch=false --browsers=ChromeHeadless"
    if [[ "$COVERAGE" == "coverage" ]]; then
        ng_args="$ng_args --code-coverage"
    fi

    local raw_output
    local ng_exit=0
    pushd "$FRONTEND_DIR" > /dev/null
    raw_output=$(npx ng test $ng_args 2>&1) || ng_exit=$?
    popd > /dev/null

    # Parse Karma/Jasmine summary line e.g.: "Executed 24 of 24 (3 FAILED)"
    local summary
    summary=$(echo "$raw_output" | grep -E "^Executed [0-9]+" | tail -1 || true)

    if [[ -n "$summary" ]]; then
        local total skipped failed
        total=$(echo  "$summary" | grep -oP '(?<=Executed )\d+' || echo "0")
        skipped=$(echo "$summary" | grep -oP '(?<=skipped )\d+' || echo "0")
        failed=$(echo  "$summary" | grep -oP '\d+(?= FAILED)'  || echo "0")
        local passed=$(( total - failed - skipped ))
        TOTAL=$(( TOTAL + total ))
        PASSED=$(( PASSED + passed ))
        FAILED=$(( FAILED + failed ))
        SKIPPED=$(( SKIPPED + skipped ))
    fi

    # Collect failing test names (lines starting with "FAILED ")
    local failures
    failures=$(echo "$raw_output" | grep -E "^FAILED " || true)
    if [[ -n "$failures" ]]; then
        FAILING_TESTS="${FAILING_TESTS}${failures}"$'\n'
        EXIT_CODE=1
    fi

    # Coverage summary from Istanbul/nyc table
    if [[ "$COVERAGE" == "coverage" ]]; then
        local cov_block
        cov_block=$(echo "$raw_output" | grep -A6 "Coverage summary" || true)
        if [[ -n "$cov_block" ]]; then
            COVERAGE_OUTPUT="${COVERAGE_OUTPUT}${cov_block}"$'\n'
        fi
    fi

    [[ $ng_exit -ne 0 ]] && EXIT_CODE=1
}

# ─── Backend (Java / Maven / JUnit 5) ────────────────────────────────────────

run_backend() {
    if ! dir_exists_or_warn "$BACKEND_DIR" "Backend"; then
        return
    fi

    require_tool java
    require_tool mvn

    echo "[backend] Running Maven tests..." >&2

    local mvn_args="test"
    if [[ "$COVERAGE" == "coverage" ]]; then
        mvn_args="verify"   # triggers jacoco:report via verify lifecycle
    fi

    local raw_output
    local mvn_exit=0
    pushd "$BACKEND_DIR" > /dev/null
    raw_output=$(mvn $mvn_args 2>&1) || mvn_exit=$?
    popd > /dev/null

    # Parse Surefire summary line e.g.: "Tests run: 42, Failures: 1, Errors: 0, Skipped: 2"
    local summary_lines
    summary_lines=$(echo "$raw_output" | grep -E "^Tests run:" || true)

    while IFS= read -r line; do
        [[ -z "$line" ]] && continue
        local t f e s
        t=$(echo "$line" | grep -oP '(?<=Tests run: )\d+'  || echo "0")
        f=$(echo "$line" | grep -oP '(?<=Failures: )\d+'   || echo "0")
        e=$(echo "$line" | grep -oP '(?<=Errors: )\d+'     || echo "0")
        s=$(echo "$line" | grep -oP '(?<=Skipped: )\d+'    || echo "0")
        local run_failed=$(( f + e ))
        TOTAL=$(( TOTAL + t ))
        PASSED=$(( PASSED + t - run_failed - s ))
        FAILED=$(( FAILED + run_failed ))
        SKIPPED=$(( SKIPPED + s ))
    done <<< "$summary_lines"

    # Collect failing test class/method names from Surefire output
    local failures
    failures=$(echo "$raw_output" | grep -E "^\[ERROR\].*FAILED" || true)
    if [[ -n "$failures" ]]; then
        FAILING_TESTS="${FAILING_TESTS}${failures}"$'\n'
        EXIT_CODE=1
    fi

    # JaCoCo coverage summary
    if [[ "$COVERAGE" == "coverage" ]]; then
        local jacoco_log="${BACKEND_DIR}/target/site/jacoco/index.html"
        if [[ -f "$jacoco_log" ]]; then
            COVERAGE_OUTPUT="${COVERAGE_OUTPUT}JaCoCo report: ${jacoco_log}"$'\n'
        fi
    fi

    [[ $mvn_exit -ne 0 ]] && EXIT_CODE=1
}

# ─── Run scopes ──────────────────────────────────────────────────────────────

case "$SCOPE" in
    frontend) run_frontend ;;
    backend)  run_backend  ;;
    all)      run_frontend; run_backend ;;
    *)
        echo "ERROR: Unknown scope '$SCOPE'. Use: frontend | backend | all" >&2
        exit 1
        ;;
esac

# ─── Structured output ───────────────────────────────────────────────────────

STATUS="PASSED"
[[ $EXIT_CODE -ne 0 ]] && STATUS="FAILED"

echo ""
echo "=== TEST RESULTS ==="
printf "Scope    : %s\n" "$SCOPE"
printf "Status   : %s\n" "$STATUS"
printf "Total    : %d\n" "$TOTAL"
printf "Passed   : %d\n" "$PASSED"
printf "Failed   : %d\n" "$FAILED"
printf "Skipped  : %d\n" "$SKIPPED"
echo ""
echo "--- Failing Tests ---"
if [[ -n "${FAILING_TESTS// }" ]]; then
    echo "$FAILING_TESTS"
else
    echo "(none)"
fi

if [[ -n "$COVERAGE_OUTPUT" ]]; then
    echo ""
    echo "--- Coverage Summary ---"
    echo "$COVERAGE_OUTPUT"
fi

printf "Exit Code: %d\n" "$EXIT_CODE"
echo "===================="

exit $EXIT_CODE

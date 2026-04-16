#!/usr/bin/env bash
# collect-metrics.sh
# Collects deterministic project state for PM planning.
# Usage: collect-metrics.sh [verbose]
#   verbose : optional — print per-directory file lists after the summary block
#
# Exit codes:
#   0 - metrics collected successfully (does not indicate build health)
#   1 - script error (missing repo root, unreadable directory)

set -uo pipefail

VERBOSE="${1:-}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../../../.." && pwd)"
TIMESTAMP="$(date -u +"%Y-%m-%dT%H:%M:%SZ")"

FRONTEND_DIR="${REPO_ROOT}/frontend"
BACKEND_DIR="${REPO_ROOT}/backend"
DATA_DIR="${REPO_ROOT}/data"

# ─── Helpers ─────────────────────────────────────────────────────────────────

# Count files matching a pattern under a directory. Returns 0 if dir absent.
count_files() {
    local dir="$1"
    local pattern="$2"
    if [[ ! -d "$dir" ]]; then
        echo "0"
        return
    fi
    find "$dir" -type f -name "$pattern" 2>/dev/null | wc -l | tr -d ' '
}

# Count files matching multiple patterns (OR). Deduplicates paths.
count_files_multi() {
    local dir="$1"
    shift
    if [[ ! -d "$dir" ]]; then
        echo "0"
        return
    fi
    local total=0
    local seen
    seen=$(mktemp)
    for pattern in "$@"; do
        while IFS= read -r f; do
            if ! grep -qxF "$f" "$seen" 2>/dev/null; then
                echo "$f" >> "$seen"
                (( total++ )) || true
            fi
        done < <(find "$dir" -type f -name "$pattern" 2>/dev/null)
    done
    rm -f "$seen"
    echo "$total"
}

# Run a command silently, return OK / ERROR / NOT_CONFIGURED
probe_tool() {
    local label="$1"
    local check_cmd="$2"   # command to test tool presence
    local run_cmd="$3"     # command to run the check
    local dir="$4"

    if [[ ! -d "$dir" ]]; then
        echo "NOT_CONFIGURED"
        return
    fi

    if ! eval "$check_cmd" &>/dev/null 2>&1; then
        echo "NOT_CONFIGURED"
        return
    fi

    local exit_code=0
    pushd "$dir" > /dev/null
    eval "$run_cmd" > /dev/null 2>&1 || exit_code=$?
    popd > /dev/null

    if [[ $exit_code -eq 0 ]]; then
        echo "OK"
    else
        echo "ERROR"
    fi
}

# Estimate phase readiness based on file presence indicators
phase_status() {
    local count="$1"
    local threshold_present="${2:-3}"
    if [[ "$count" -eq 0 ]]; then
        echo "NOT_STARTED"
    elif [[ "$count" -lt "$threshold_present" ]]; then
        echo "IN_PROGRESS"
    else
        echo "PRESENT"
    fi
}

# ─── Frontend counts ─────────────────────────────────────────────────────────

FE_COMPONENTS=0
FE_SERVICES=0
FE_MODELS=0
FE_PIPES_DIRECTIVES=0
FE_GUARDS_RESOLVERS=0
FE_CONFIG_UTILS=0
FE_TEMPLATES=0
FE_STYLES=0
FE_SPECS=0
FE_TOTAL=0

if [[ -d "$FRONTEND_DIR" ]]; then
    FE_COMPONENTS=$(count_files "$FRONTEND_DIR" "*.component.ts")
    FE_SERVICES=$(count_files   "$FRONTEND_DIR" "*.service.ts")
    FE_MODELS=$(count_files_multi "$FRONTEND_DIR" "*.model.ts" "*.interface.ts" "*.types.ts")
    FE_PIPES_DIRECTIVES=$(count_files_multi "$FRONTEND_DIR" "*.pipe.ts" "*.directive.ts")
    FE_GUARDS_RESOLVERS=$(count_files_multi "$FRONTEND_DIR" "*.guard.ts" "*.resolver.ts")
    FE_CONFIG_UTILS=$(count_files_multi "$FRONTEND_DIR" "*.config.ts" "*.util.ts")
    FE_TEMPLATES=$(count_files  "$FRONTEND_DIR" "*.html")
    FE_STYLES=$(count_files_multi "$FRONTEND_DIR" "*.scss" "*.css")
    FE_SPECS=$(count_files      "$FRONTEND_DIR" "*.spec.ts")
    FE_TOTAL=$(( FE_COMPONENTS + FE_SERVICES + FE_MODELS + FE_PIPES_DIRECTIVES \
               + FE_GUARDS_RESOLVERS + FE_CONFIG_UTILS + FE_TEMPLATES + FE_STYLES ))
fi

# ─── Backend counts ──────────────────────────────────────────────────────────

BE_CONTROLLERS=0
BE_SERVICES=0
BE_DTOS=0
BE_REPOS=0
BE_CONFIG_UTIL=0
BE_ENUMS=0
BE_UNIT_TESTS=0
BE_IT_TESTS=0
BE_TOTAL=0
BE_DOMAIN=0

if [[ -d "$BACKEND_DIR" ]]; then
    BE_CONTROLLERS=$(count_files "$BACKEND_DIR/src/main" "*Controller.java")
    BE_SERVICES=$(count_files_multi "$BACKEND_DIR/src/main" "*Service.java" "*ServiceImpl.java")
    BE_DTOS=$(count_files_multi "$BACKEND_DIR/src/main" \
        "*Dto.java" "*DTO.java" "*Request.java" "*Response.java" "*Record.java")
    BE_REPOS=$(count_files "$BACKEND_DIR/src/main" "*Repository.java")
    BE_CONFIG_UTIL=$(count_files_multi "$BACKEND_DIR/src/main" \
        "*Config.java" "*Configuration.java" "*Util.java" "*Helper.java")
    BE_ENUMS=$(count_files_multi "$BACKEND_DIR/src/main" \
        "*Enum.java" "RiskTier.java" "*Type.java" "*Signal.java")
    BE_UNIT_TESTS=$(count_files_multi "$BACKEND_DIR/src/test" "*Test.java" "*Tests.java")
    BE_IT_TESTS=$(count_files_multi   "$BACKEND_DIR/src/test" "*IT.java" "*ITCase.java")

    # Domain models = all .java in src/main excluding already-counted categories
    local all_main_java
    all_main_java=$(find "$BACKEND_DIR/src/main" -type f -name "*.java" 2>/dev/null | wc -l | tr -d ' ')
    BE_DOMAIN=$(( all_main_java - BE_CONTROLLERS - BE_SERVICES - BE_DTOS \
                - BE_REPOS - BE_CONFIG_UTIL - BE_ENUMS ))
    [[ $BE_DOMAIN -lt 0 ]] && BE_DOMAIN=0

    BE_TOTAL=$(( all_main_java ))
fi

# ─── Data layer counts ───────────────────────────────────────────────────────

DATA_JSON=0
[[ -d "$DATA_DIR" ]] && DATA_JSON=$(find "$DATA_DIR" -type f -name "*.json" 2>/dev/null | wc -l | tr -d ' ')

FE_CONFIGS=$(count_files_multi \
    "${FRONTEND_DIR:-/dev/null}" "*.config.ts" "app.config.ts" 2>/dev/null || echo "0")
BE_CONFIGS=$(count_files_multi \
    "${BACKEND_DIR:-/dev/null}/src/main" "*Config.java" "*Configuration.java" 2>/dev/null || echo "0")
DATA_CONFIGS=$(( FE_CONFIGS + BE_CONFIGS ))

# ─── Total tests ─────────────────────────────────────────────────────────────

TOTAL_TESTS=$(( FE_SPECS + BE_UNIT_TESTS + BE_IT_TESTS ))

# ─── Lint / Build signals ─────────────────────────────────────────────────────

FE_LINT_STATUS="NOT_CONFIGURED"
FE_BUILD_STATUS="NOT_CONFIGURED"
BE_COMPILE_STATUS="NOT_CONFIGURED"
BE_CHECKSTYLE_STATUS="NOT_CONFIGURED"

if [[ -d "$FRONTEND_DIR" ]]; then
    # ESLint: check for config then try dry-run
    if [[ -f "$FRONTEND_DIR/.eslintrc.json" ]] || [[ -f "$FRONTEND_DIR/eslint.config.js" ]] \
        || [[ -f "$FRONTEND_DIR/.eslintrc.js" ]]; then
        FE_LINT_STATUS=$(probe_tool "eslint" \
            "command -v npx" \
            "npx eslint --max-warnings=0 src/ --quiet" \
            "$FRONTEND_DIR")
    fi

    # Angular build dry-run
    if command -v npx &>/dev/null; then
        FE_BUILD_STATUS=$(probe_tool "ng build" \
            "command -v npx" \
            "npx ng build --configuration=production --dry-run 2>/dev/null || npx ng build --aot --dry-run" \
            "$FRONTEND_DIR")
    fi
fi

if [[ -d "$BACKEND_DIR" ]] && command -v mvn &>/dev/null; then
    BE_COMPILE_STATUS=$(probe_tool "mvn compile" \
        "command -v mvn" \
        "mvn compile -q" \
        "$BACKEND_DIR")

    # Checkstyle — only if plugin is configured
    if grep -q "checkstyle" "${BACKEND_DIR}/pom.xml" 2>/dev/null; then
        BE_CHECKSTYLE_STATUS=$(probe_tool "mvn checkstyle" \
            "command -v mvn" \
            "mvn checkstyle:check -q" \
            "$BACKEND_DIR")
    fi
fi

# ─── Phase readiness ─────────────────────────────────────────────────────────

PH1_STATUS=$(phase_status "$FE_MODELS" 3)                                # Data models
PH2_STATUS=$(phase_status "$FE_SERVICES" 2)                              # Risk logic services
PH3_STATUS=$(phase_status "$(count_files "${FRONTEND_DIR:-/x}" "*aggregat*.ts")" 1)  # 24h aggregation
PH4_STATUS=$(phase_status "$FE_COMPONENTS" 2)                            # Frontend UI components
PH5_STATUS=$(phase_status "$(count_files_multi \
    "${FRONTEND_DIR:-/x}" "*timeline*" "*chart*" 2>/dev/null || echo 0)" 1)  # Timeline
PH6_STATUS=$(phase_status "$(count_files_multi \
    "${FRONTEND_DIR:-/x}" "*data-access*.ts" "*api*.service.ts" 2>/dev/null || echo 0)" 1)  # API layer
PH7_STATUS=$(phase_status "$BE_TOTAL" 3)                                 # Java backend

# ─── Output ───────────────────────────────────────────────────────────────────

echo ""
echo "=== PROJECT METRICS ==="
printf "Timestamp  : %s\n" "$TIMESTAMP"
printf "Repo Root  : %s\n" "$REPO_ROOT"

echo ""
echo "--- Frontend Source (Angular) ---"
printf "Components          : %-4s (.component.ts)\n"            "$FE_COMPONENTS"
printf "Services            : %-4s (.service.ts)\n"              "$FE_SERVICES"
printf "Models / Interfaces : %-4s (.model.ts | .interface.ts | .types.ts)\n" "$FE_MODELS"
printf "Pipes / Directives  : %-4s (.pipe.ts | .directive.ts)\n" "$FE_PIPES_DIRECTIVES"
printf "Guards / Resolvers  : %-4s (.guard.ts | .resolver.ts)\n" "$FE_GUARDS_RESOLVERS"
printf "Config / Utils      : %-4s (.config.ts | .util.ts)\n"    "$FE_CONFIG_UTILS"
printf "Templates           : %-4s (.html)\n"                    "$FE_TEMPLATES"
printf "Styles              : %-4s (.scss | .css)\n"             "$FE_STYLES"
printf "Frontend Spec Tests : %-4s (.spec.ts)\n"                 "$FE_SPECS"
printf "Total Frontend Src  : %-4s\n"                            "$FE_TOTAL"

echo ""
echo "--- Backend Source (Java) ---"
printf "Controllers         : %-4s (*Controller.java)\n"          "$BE_CONTROLLERS"
printf "Services            : %-4s (*Service.java | *ServiceImpl)\n" "$BE_SERVICES"
printf "DTOs / Records      : %-4s (*Dto | *Request | *Response)\n" "$BE_DTOS"
printf "Repositories        : %-4s (*Repository.java)\n"          "$BE_REPOS"
printf "Config / Util       : %-4s (*Config | *Util | *Helper)\n" "$BE_CONFIG_UTIL"
printf "Enums               : %-4s (*Enum | RiskTier | *Type)\n"  "$BE_ENUMS"
printf "Domain Models       : %-4s (remaining .java src/main)\n"  "$BE_DOMAIN"
printf "Backend Unit Tests  : %-4s (*Test.java | *Tests.java)\n"  "$BE_UNIT_TESTS"
printf "Backend IT Tests    : %-4s (*IT.java | *ITCase.java)\n"   "$BE_IT_TESTS"
printf "Total Backend Src   : %-4s\n"                             "$BE_TOTAL"

echo ""
echo "--- Data Layer ---"
printf "Mock Data Files     : %-4s (data/**/*.json)\n"  "$DATA_JSON"
printf "Config Files        : %-4s (fe config.ts + be *Config.java)\n" "$DATA_CONFIGS"

echo ""
echo "--- Test Summary ---"
printf "Total Spec Tests    : %-4s (frontend .spec.ts)\n"         "$FE_SPECS"
printf "Total Unit Tests    : %-4s (backend *Test.java + *Tests)\n" "$BE_UNIT_TESTS"
printf "Total IT Tests      : %-4s (backend *IT.java + *ITCase)\n"  "$BE_IT_TESTS"
printf "Total Tests Overall : %-4s\n"                              "$TOTAL_TESTS"

echo ""
echo "--- Lint / Build Signals ---"
printf "Frontend ESLint     : %s\n" "$FE_LINT_STATUS"
printf "Frontend Build      : %s\n" "$FE_BUILD_STATUS"
printf "Backend Compile     : %s\n" "$BE_COMPILE_STATUS"
printf "Backend Checkstyle  : %s\n" "$BE_CHECKSTYLE_STATUS"

echo ""
echo "--- Phase Readiness ---"
printf "Phase 1 (Data Models)     : %s\n" "$PH1_STATUS"
printf "Phase 2 (Risk Logic)      : %s\n" "$PH2_STATUS"
printf "Phase 3 (24h Aggregation) : %s\n" "$PH3_STATUS"
printf "Phase 4 (Frontend UI)     : %s\n" "$PH4_STATUS"
printf "Phase 5 (Timeline)        : %s\n" "$PH5_STATUS"
printf "Phase 6 (API Layer)       : %s\n" "$PH6_STATUS"
printf "Phase 7 (Java Backend)    : %s\n" "$PH7_STATUS"

echo "======================"

# ─── Verbose file lists ───────────────────────────────────────────────────────

if [[ "$VERBOSE" == "verbose" ]]; then
    echo ""
    echo "=== VERBOSE FILE LISTS ==="

    if [[ -d "$FRONTEND_DIR" ]]; then
        echo ""
        echo "--- Frontend Components ---"
        find "$FRONTEND_DIR" -type f -name "*.component.ts" 2>/dev/null | sort | sed "s|$REPO_ROOT/||"

        echo ""
        echo "--- Frontend Services ---"
        find "$FRONTEND_DIR" -type f -name "*.service.ts" 2>/dev/null | sort | sed "s|$REPO_ROOT/||"

        echo ""
        echo "--- Frontend Specs ---"
        find "$FRONTEND_DIR" -type f -name "*.spec.ts" 2>/dev/null | sort | sed "s|$REPO_ROOT/||"
    fi

    if [[ -d "$BACKEND_DIR" ]]; then
        echo ""
        echo "--- Backend Java Source ---"
        find "$BACKEND_DIR/src/main" -type f -name "*.java" 2>/dev/null | sort | sed "s|$REPO_ROOT/||"

        echo ""
        echo "--- Backend Tests ---"
        find "$BACKEND_DIR/src/test" -type f -name "*.java" 2>/dev/null | sort | sed "s|$REPO_ROOT/||"
    fi

    if [[ -d "$DATA_DIR" ]]; then
        echo ""
        echo "--- Data Files ---"
        find "$DATA_DIR" -type f -name "*.json" 2>/dev/null | sort | sed "s|$REPO_ROOT/||"
    fi

    echo ""
    echo "=========================="
fi

exit 0

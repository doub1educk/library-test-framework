#!/usr/bin/env bash
set -euo pipefail

reports_dir="${1:?Укажите каталог с отчётами JUnit XML}"
: "${RADAR_URL:?Нужна переменная RADAR_URL}"
: "${RADAR_PROJECT:?Нужна переменная RADAR_PROJECT}"
: "${RADAR_API_KEY:?Нужна переменная RADAR_API_KEY}"

branch="${RADAR_BRANCH:-${GITHUB_HEAD_REF:-${GITHUB_REF_NAME:-$(git rev-parse --abbrev-ref HEAD)}}}"
commit="${RADAR_COMMIT:-${GITHUB_SHA:-$(git rev-parse HEAD)}}"
attempt="${RADAR_ATTEMPT:-${GITHUB_RUN_ATTEMPT:-1}}"
ci_run="${RADAR_CI_RUN:-${GITHUB_RUN_ID:-}}"
build_url="${RADAR_BUILD_URL:-}"
if [[ -z "$build_url" && -n "${GITHUB_RUN_ID:-}" ]]; then
  build_url="${GITHUB_SERVER_URL}/${GITHUB_REPOSITORY}/actions/runs/${GITHUB_RUN_ID}"
fi
enforce="${RADAR_ENFORCE:-true}"
api="${RADAR_URL%/}/api/v1/projects/${RADAR_PROJECT}"

shopt -s nullglob
files=("$reports_dir"/TEST-*.xml "$reports_dir"/*.junit.xml)
if [[ ${#files[@]} -eq 0 ]]; then
  echo "В $reports_dir нет файлов отчётов" >&2
  exit 2
fi

form=()
for file in "${files[@]}"; do
  form+=(-F "report=@${file};type=application/xml")
done
form+=(-F "branch=${branch}" -F "commit=${commit}" -F "attempt=${attempt}")
[[ -n "$ci_run" ]] && form+=(-F "ciRunId=${ci_run}")
[[ -n "$build_url" ]] && form+=(-F "buildUrl=${build_url}")

echo "Flaky Radar: отправляю ${#files[@]} файл(ов), ветка ${branch}, коммит ${commit:0:7}, попытка ${attempt}"
response="$(curl -sS --fail-with-body -H "X-Api-Key: ${RADAR_API_KEY}" "${form[@]}" "${api}/runs")"
run_id="$(jq -r '.run.id' <<<"$response")"

curl -sS --fail "${api}/runs/${run_id}/summary.md" -o radar-summary.md
decision="$(curl -sS --fail "${api}/runs/${run_id}/gate" | jq -r '.decision')"

echo "Flaky Radar: прогон #${run_id}, решение ${decision}"
if [[ -n "${GITHUB_OUTPUT:-}" ]]; then
  {
    echo "run_id=${run_id}"
    echo "decision=${decision}"
  } >>"$GITHUB_OUTPUT"
fi

if [[ "$enforce" == "true" && "$decision" == "FAIL" ]]; then
  echo "Flaky Radar: есть новые или давно сломанные падения, сборка остановлена" >&2
  exit 1
fi

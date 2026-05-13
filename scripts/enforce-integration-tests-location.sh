#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"

is_policy_violation() {
  local file="$1"

  if [[ "$file" =~ (IT|IntegrationTest)\.java$ ]]; then
    return 0
  fi

  if grep -Eq '@(SpringBootTest|WebMvcTest|DataJpaTest|JdbcTest|AutoConfigureMockMvc|Testcontainers|ContextConfiguration)' "$file"; then
    return 0
  fi

  return 1
}

violations=()
while IFS= read -r file; do
  if is_policy_violation "$file"; then
    violations+=("${file#"$ROOT_DIR/"}")
  fi
done < <(find "$ROOT_DIR" -type f -path '*/src/test/java/*' -name '*.java' ! -path '*/hex-application/*')

if ((${#violations[@]} > 0)); then
  echo "Integration test policy violation: integration tests must be only in hex-application." >&2
  printf ' - %s\n' "${violations[@]}" >&2
  exit 1
fi


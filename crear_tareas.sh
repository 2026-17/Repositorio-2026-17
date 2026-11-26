#!/usr/bin/env bash
set -euo pipefail

REPO="2026-17/Repositorio-2026-17"
OWNER="2026-17"
PROJECT_NUM=1
PROJECT_ID="PVT_kwDOD7jnJ84BQFIk"
WORK_FIELD_ID="PVTF_lADOD7jnJ84BQFIkzhZyDCo"

declare -A USERS=(
  [Roy]="RoyG247"
  [Sebastian]="NashiAU"
  [Carlos]="Carlos175214"
  [Isaac]="IsaacHollow"
  [Aslehy]="aslehyaguilarperez-wq"
  [Emmanuel]="EmmanuelNunez0802"
)

PARENT=49

TAREAS='HU-033|Frontend|Selector de programa/nivel y vista de malla|14|Roy
HU-033|Pruebas unitarias|Filtrado por programa y nivel|10|Aslehy
HU-033|Pruebas funcionales|Consulta de malla de punta a punta|12|Isaac'

# Reintenta con espera creciente (30s, 60s) para no agotar la cuota
retry() {
  local i wait=30
  for i in 1 2 3; do
    if "$@"; then return 0; fi
    echo "  falló, esperando ${wait}s (intento $i)..." >&2
    sleep "$wait"
    wait=$((wait * 2))
  done
  return 1
}

# Issues existentes por REST (cuota aparte de GraphQL)
EXISTING=$(gh api "repos/$REPO/issues?state=all&per_page=100" --paginate \
  --jq '.[] | "\(.number)\t\(.title)"')

while IFS='|' read -r hu tipo desc horas persona <&3; do
  [[ -z "$hu" ]] && continue

  title="[${hu}] ${tipo}: ${desc}"
  body=$(printf 'HU padre: %s\nTipo: %s\nHoras estimadas: %sh\nResponsable: %s\n\n- [ ] %s\n- [ ] Pruebas aprobadas\n- [ ] Criterios de aceptación cumplidos' \
    "$hu" "$tipo" "$horas" "$persona" "$desc")

  assignee_args=()
  [[ -n "${USERS[$persona]:-}" ]] && assignee_args=(--assignee "${USERS[$persona]}")

  num=$(awk -F'\t' -v t="$title" '$2==t {print $1; exit}' <<< "$EXISTING")
  if [[ -n "$num" ]]; then
    echo "YA EXISTE: $title (#$num)"
  else
    url=$(retry gh issue create -R "$REPO" --title "$title" --body "$body" "${assignee_args[@]}")
    num=${url##*/}
    echo "CREADO: $title (#$num)"
    sleep 5
  fi

  child_id=$(retry gh api "repos/$REPO/issues/$num" --jq '.id')
  retry gh api -X POST "repos/$REPO/issues/$PARENT/sub_issues" \
    -F sub_issue_id="$child_id" >/dev/null 2>&1 || echo "  aviso: sub-issue no enlazado (quizá ya estaba)" >&2
  sleep 5

  url="https://github.com/$REPO/issues/$num"
  retry gh project item-add "$PROJECT_NUM" --owner "$OWNER" --url "$url" >/dev/null 2>&1 || true
  sleep 5

  item_id=$(retry gh project item-list "$PROJECT_NUM" --owner "$OWNER" --limit 500 --format json \
    --jq ".items[] | select(.content.url==\"$url\") | .id")

  if [[ -z "$item_id" ]]; then
    echo "ERROR: $url no aparece en el proyecto. Agrégalo a mano y vuelve a correr." >&2
    exit 1
  fi

  retry gh project item-edit --id "$item_id" --project-id "$PROJECT_ID" \
    --field-id "$WORK_FIELD_ID" --number "$horas" >/dev/null

  echo "OK: $title (Work=$horas)"
  sleep 5
done 3<<< "$TAREAS"

echo "Listo: tareas de HU-033 procesadas."
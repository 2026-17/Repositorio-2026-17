import { ChevronLeft, ChevronRight } from "lucide-react";

// «Mostrando 21–40 de 312» + anterior/siguiente y páginas. page empieza en 1.
export default function Pagination({ page, pageSize, total, onChange }) {
    const paginas = Math.max(1, Math.ceil(total / pageSize));
    const desde = total === 0 ? 0 : (page - 1) * pageSize + 1;
    const hasta = Math.min(total, page * pageSize);
    const nums = [];
    for (let p = Math.max(1, page - 1); p <= Math.min(paginas, page + 1); p++) nums.push(p);
    if (nums[0] > 1) {
        if (nums[0] > 2) nums.unshift("…");
        nums.unshift(1);
    }
    if (nums[nums.length - 1] < paginas) {
        if (nums[nums.length - 1] < paginas - 1) nums.push("…");
        nums.push(paginas);
    }

    return (
        <nav className="sg-pager" aria-label="Paginación">
            <p className="sg-pager__count">
                Mostrando <strong>{desde}–{hasta}</strong> de <strong>{total}</strong>
            </p>
            <div className="sg-pager__controls">
                <button type="button" className="sg-pager__btn" onClick={() => onChange(page - 1)} disabled={page <= 1} aria-label="Página anterior">
                    <ChevronLeft size={18} strokeWidth={1.75} aria-hidden="true" />
                </button>
                {nums.map((n, i) => (n === "…"
                    ? <span key={`e${i}`} className="sg-pager__gap" aria-hidden="true">…</span>
                    : (
                        <button
                            key={n}
                            type="button"
                            className={`sg-pager__btn${n === page ? " is-current" : ""}`}
                            onClick={() => onChange(n)}
                            aria-current={n === page ? "page" : undefined}
                            aria-label={`Página ${n}`}
                        >
                            {n}
                        </button>
                    )))}
                <button type="button" className="sg-pager__btn" onClick={() => onChange(page + 1)} disabled={page >= paginas} aria-label="Página siguiente">
                    <ChevronRight size={18} strokeWidth={1.75} aria-hidden="true" />
                </button>
            </div>
        </nav>
    );
}


export const PAGE_SIZE = 25

interface Props {
  /** Zero-based page number. */
  page: number
  total: number
  onPage: (page: number) => void
}

/** Previous / next buttons under a list. The list is cut into pages on this device. */
export function Pager({ page, total, onPage }: Props) {
  if (total <= PAGE_SIZE) {
    return null
  }
  const first = page * PAGE_SIZE + 1
  const last = Math.min(total, (page + 1) * PAGE_SIZE)
  return (
    <div className="row between pager">
      <span className="muted">
        {first}-{last} of {total}
      </span>
      <span className="row">
        <button type="button" disabled={page === 0} onClick={() => onPage(page - 1)}>
          Previous
        </button>
        <button type="button" disabled={last >= total} onClick={() => onPage(page + 1)}>
          Next
        </button>
      </span>
    </div>
  )
}

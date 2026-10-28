interface Props {
  title: string
  note: string
}

/** Placeholder for a page whose backend part is not ready yet. */
export function ComingSoonPage({ title, note }: Props) {
  return (
    <section>
      <h2>{title}</h2>
      <p className="muted">{note}</p>
    </section>
  )
}

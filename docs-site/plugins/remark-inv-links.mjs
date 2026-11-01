// Turn every INV-XXX-N mention in prose into a link to its entry on the
// invariant index page. Skips headings (the index's own anchors), existing
// links, and code — inline code is a distinct node type and is never visited.

const INV_RE = /INV-[A-Z0-9]+-\d+/g;
const SKIP = new Set(['heading', 'link', 'linkReference']);

export default function remarkInvLinks() {
  return (tree) => walk(tree);
}

function walk(node) {
  if (!node.children || SKIP.has(node.type)) return;
  node.children = node.children.flatMap((child) => {
    if (child.type === 'text') return linkify(child);
    walk(child);
    return [child];
  });
}

function linkify(textNode) {
  const value = textNode.value;
  const parts = [];
  let last = 0;
  for (const m of value.matchAll(INV_RE)) {
    if (m.index > last) parts.push({ type: 'text', value: value.slice(last, m.index) });
    parts.push({
      type: 'link',
      url: `/invariants/#${m[0].toLowerCase()}`,
      children: [{ type: 'text', value: m[0] }],
    });
    last = m.index + m[0].length;
  }
  if (parts.length === 0) return [textNode];
  if (last < value.length) parts.push({ type: 'text', value: value.slice(last) });
  return parts;
}

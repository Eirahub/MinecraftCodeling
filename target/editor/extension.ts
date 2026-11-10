// Editor extension (pxtarget.json appTheme.extendEditor), loaded by pxt as editor.js.
// blocklyPatch runs on every Blocks file before it loads, so projects saved by an older editor can be upgraded.

namespace codeling.editor {
    // Commit d506004 (issue #125) turned the Block dropdown of these blocks into an item input (codeling_item shadow).
    // A project saved before that has <field name="item">Block.Sandstone</field> where the block now has an input, and
    // the value was lost on load (the slot came back as 0, which setItem reads as air). Turn the field into the
    // codeling_block reporter the editor makes for a Block argument today, so the value survives.
    const ITEM_SLOTS: { [blockType: string]: string } = {
        agent_set_item: "blockOrItem",
        agent_take_item: "item",
        agent_container_count: "item",
    };

    export function upgradeItemFields(root: Element): void {
        const doc = root.ownerDocument;
        for (const block of Array.from(root.getElementsByTagName("block"))) {
            const slot = ITEM_SLOTS[block.getAttribute("type")];
            const field = slot && Array.from(block.children).find(c => c.tagName === "field" && c.getAttribute("name") === slot);
            if (!field) continue;
            const value = doc.createElementNS(block.namespaceURI, "value");
            value.setAttribute("name", slot);
            const shadow = doc.createElementNS(block.namespaceURI, "shadow");
            shadow.setAttribute("type", "codeling_item");
            shadow.appendChild(fieldOf(doc, block.namespaceURI, "item", "Item.Coal"));
            const reporter = doc.createElementNS(block.namespaceURI, "block");
            reporter.setAttribute("type", "codeling_block");
            reporter.appendChild(fieldOf(doc, block.namespaceURI, "block", field.textContent));
            value.appendChild(shadow);
            value.appendChild(reporter);
            block.replaceChild(value, field);
        }
    }

    function fieldOf(doc: Document, ns: string, name: string, text: string): Element {
        const field = doc.createElementNS(ns, "field");
        field.setAttribute("name", name);
        field.textContent = text;
        return field;
    }

    (window as any).pxt.editor.initExtensionsAsync = () => Promise.resolve({
        blocklyPatch: (pkgTargetVersion: string, dom: Element) => upgradeItemFields(dom),
    });
}

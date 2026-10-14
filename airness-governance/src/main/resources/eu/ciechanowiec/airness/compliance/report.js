(() => {
  const data = JSON.parse(document.getElementById("report-data").textContent);
  const save = (name, type, text) => {
    const link = document.createElement("a");
    const address = URL.createObjectURL(new Blob([text], { type }));
    link.setAttribute("href", address);
    link.setAttribute("download", name);
    document.body.appendChild(link);
    link.click();
    setTimeout(() => {
      URL.revokeObjectURL(address);
      link.remove();
    }, 0);
  };

  document.querySelectorAll("[data-download]").forEach((button) => {
    button.removeAttribute("hidden");
    button.addEventListener("click", () => {
      const key = button.getAttribute("data-download");
      const type = key === "sbom" ? "application/vnd.cyclonedx+json" : "text/csv";
      save(button.getAttribute("data-file"), type, data[key]);
    });
  });

  const table = document.getElementById("inventory-table");
  const body = table.tBodies[0];
  const rows = Array.from(body.rows);
  const search = document.getElementById("filter-text");
  const scope = document.getElementById("filter-scope");
  const kind = document.getElementById("filter-class");
  const shown = document.getElementById("filter-count");

  const matches = (row) => {
    const needle = search.value.trim().toLowerCase();
    return (!needle || row.textContent.toLowerCase().includes(needle)) &&
      (!scope.value || row.dataset.scope.split(" ").includes(scope.value)) &&
      (!kind.value || row.dataset.class === kind.value);
  };
  const apply = () => {
    rows.forEach((row) => row.toggleAttribute("hidden", !matches(row)));
    shown.replaceChildren(`${rows.filter(matches).length} of ${rows.length} artifact versions`);
  };
  [search, scope, kind].forEach((control) => control.addEventListener("input", apply));

  table.querySelectorAll("th[data-sort]").forEach((header, index) => {
    header.setAttribute("tabindex", "0");
    const sort = () => {
      const ascending = header.getAttribute("aria-sort") !== "ascending";
      table.querySelectorAll("th").forEach((other) => other.removeAttribute("aria-sort"));
      header.setAttribute("aria-sort", ascending ? "ascending" : "descending");
      const ordered = rows.toSorted((left, right) => {
        const leftText = left.cells[index].textContent;
        const rightText = right.cells[index].textContent;
        return (ascending ? 1 : -1) * leftText.localeCompare(rightText, undefined, {
          numeric: true,
          sensitivity: "base",
        });
      });
      body.replaceChildren(...ordered);
    };
    header.addEventListener("click", sort);
    header.addEventListener("keydown", (event) => {
      if (event.key === "Enter" || event.key === " ") {
        event.preventDefault();
        sort();
      }
    });
  });
  apply();
})();

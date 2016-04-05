package com.eric;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.beust.jcommander.Parameter;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class TableToJson extends Command {

	@Parameter(description = "urls")
	private List<String> urls = new ArrayList<>();

	@Parameter(names = { "-t", "--table-selector" }, description = "The css selector to grab the table(s) to pull data from on each url.")
	private String tableSelector = "table:eq(0)";

	@Parameter(names = { "-th", "--header-cell-selector" }, description = "The css selector to grab the table header names.")
	private String headerCellSelector = "thead tr:eq(0) th";

	@Parameter(names = { "-tr", "--row-selector" }, description = "The css selector to grab the table rows.")
	private String rowSelector = "tbody tr";

	@Parameter(names = { "-td", "--row-cell-selector" }, description = "The css selector to grab the table cell data within each row.")
	private String rowCellSelector = "td";

	@Parameter(names = { "-p", "--pretty" }, description = "Print json in a more human-readable way.")
	private boolean pretty;

	@Parameter(names = { "-s", "--string-values" }, description = "If set, all values will be treated as Strings.  If not set, boolean and number values will be converted to the appropriate type.")
	private boolean useStringValues;

	@Parameter(names = { "-l", "--include-links" }, description = "If set, and a link is encountered in table data, it is included in the json output with '_link' appended to the field name.")
	private boolean includeLinks;

	@Parameter(names = { "-o", "--omit-partial-rows" }, description = "Omits rows that don't have values for each column, e.g. a colspan is used.  If set, this can be successful in removing summary rows from output.")
	private boolean omitPartialRows;

	private List<String> columnNames = new ArrayList<>();

	private List<Map<String, Object>> rows = new ArrayList<>();

	public static void main(String[] args) {
		Command.main(new TableToJson(), args);
	}

	@Override
	protected String getProgramName() {
		return "table-to-json";
	}

	@Override
	protected void validate(Collection<String> messages) {
		if (urls.isEmpty()) {
			messages.add("You must specify at least one url to read.");
		}
	}

	@Override
	protected void run() throws Exception {
		for (String url : urls) {
			verbose("Reading data from url %s", url);
			Document doc = Jsoup.connect(url).get();
			for (Element table : doc.select(tableSelector)) {
				extractHeaders(table);
				extractRows(table);
			}
		}

		Gson gson = null;
		if (pretty) {
			gson = new GsonBuilder().setPrettyPrinting().create();
		} else {
			gson = new Gson();
		}

		out(gson.toJson(rows));
		verbose("%s rows.", rows.size());
	}

	private void extractHeaders(Element table) {
		columnNames.clear();

		for (Element cell : table.select(headerCellSelector)) {
			if (cell.text() != null) {
				columnNames.add(cell.text().toLowerCase());
			}
		}

		verbose("   Found %s columns.", columnNames.size());
	}

	private Object numberOrString(String v) {
		try {
			return Integer.valueOf(v);
		} catch (NumberFormatException nfe) {
			try {
				return Double.valueOf(v);
			} catch (NumberFormatException nfe2) {
			}
		}

		return v;
	}

	private void extractRows(Element table) {
		int len = rows.size();

		for (Element row : table.select(rowSelector)) {
			Map<String, Object> r = new LinkedHashMap<>();
			for (Element td : row.select(rowCellSelector)) {
				String strVal = td.text();
				Object val = null;

				if (useStringValues) {
					val = strVal;
				} else {
					if ("true".equalsIgnoreCase(strVal)) {
						val = true;
					} else if ("false".equalsIgnoreCase(strVal)) {
						val = false;
					} else {
						val = numberOrString(strVal);
					}
				}

				r.put(columnNames.get(td.siblingIndex()), val);

				if (includeLinks) {
					Elements links = td.select("a:eq(0)");
					if (!links.isEmpty()) {
						r.put(columnNames.get(td.siblingIndex()) + "_link",
								links.first().attr("abs:href"));
					}
				}
			}

			if (!omitPartialRows || r.size() >= columnNames.size()) {
				rows.add(r);
			}
		}

		verbose("   Added %s rows.", rows.size() - len);
	}
}

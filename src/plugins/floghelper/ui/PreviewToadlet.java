/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.floghelper.ui;

import freenet.client.DefaultMIMETypes;
import freenet.client.HighLevelSimpleClient;
import freenet.clients.http.PageNode;
import freenet.clients.http.ToadletContext;
import freenet.clients.http.ToadletContextClosedException;
import freenet.pluginmanager.PluginStore;
import freenet.support.MultiValueTable;
import freenet.support.api.HTTPRequest;
import java.io.IOException;
import java.net.URI;
import plugins.floghelper.FlogHelper;
import plugins.floghelper.data.Attachment;
import plugins.floghelper.data.Flog;
import plugins.floghelper.data.pluginstore.PluginStoreFlog;
import plugins.floghelper.ui.flog.FlogFactory;
import plugins.floghelper.ui.flog.IndexBuilder;

/**
 * This toadlet does all the "offline" previewing of flogs. The result should be
 * exactly the same than what will be inserted, except that the returned code here
 * isn't filtered in any way.
 *
 * @author Artefact2
 */
public class PreviewToadlet extends FlogHelperToadlet {

	public static final String MY_URI = "/Preview/";
	public static final String VIEW_RAW_DEFAULT_TEMPLATE_URI = "DefaultTemplate/";
	public static final String VIEW_DEFAULT_CSS_URI = "DefaultCSS/";

	public PreviewToadlet(HighLevelSimpleClient hlsc) {
		super(hlsc, MY_URI);
	}

	@Override
	public void getPageGet(PageNode pageNode, URI uri, HTTPRequest request, ToadletContext ctx) throws ToadletContextClosedException, IOException {
		this.getPagePost(pageNode, uri, request, ctx);
	}

	@Override
	public void getPagePost(PageNode pageNode, URI uri, HTTPRequest request, ToadletContext ctx) throws ToadletContextClosedException, IOException {
		final String flogID = uri.getPath().replace(FlogHelperToadlet.BASE_URI + PreviewToadlet.MY_URI, "").split("/")[0];
		final Flog flog = new PluginStoreFlog(flogID);
		if (flog == null) {
			this.sendErrorPage(ctx, 404, "Not found", "Incorrect or missing FlogID.");
		} else {
			final FlogFactory factory = new FlogFactory(flog);
			final String file = uri.getPath().replace(FlogHelperToadlet.BASE_URI + PreviewToadlet.MY_URI, "").replace(flogID, "");
			if (file.equals("/") || file.equals("/index.html")) {
				writeHTMLReply(ctx, 200, "OK", null, appendPreviewWarning(factory.getIndex()));
			} else if (file.startsWith("/Content-") && file.endsWith(".html")) {
				final String contentID = file.replace("/Content-", "").replace(".html", "");
				writeHTMLReply(ctx, 200, "OK", null, appendPreviewWarning(factory.getContentPage(contentID)));
			} else if (file.startsWith("/Archives-p") && file.endsWith(".html")) {
				writeHTMLReply(ctx, 200, "OK", null, appendPreviewWarning(factory.getArchives(Long.parseLong(file.replace("/Archives-p", "").replace(".html", "")))));
			} else if (file.startsWith("/Tag-") && file.endsWith(".html")) {
				final long page = Long.parseLong(file.replaceAll("^/Tag-(.+?)-p([0-9]+)\\.html$", "$2"));
				final String tag = file.replaceAll("^/Tag-(.+?)-p([0-9]+)\\.html$", "$1");
				writeHTMLReply(ctx, 200, "OK", null, appendPreviewWarning(factory.getTagsPage(tag, page)));
			} else if (file.equals("/GlobalStyle.css")) {
				byte[] data = new FlogFactory(flog).getCSS().getBytes();
				ctx.sendReplyHeaders(200, "OK", new MultiValueTable<String, String>(), "text/css", data.length);
				ctx.writeData(data);
			} else if (file.equals("/AtomFeed.xml")) {
				byte[] data = new FlogFactory(flog).getAtomFeed().getBytes();
				ctx.sendReplyHeaders(200, "OK", new MultiValueTable<String, String>(), "application/atom+xml", data.length);
				ctx.writeData(data);
			} else if (file.equals("/activelink.png")) {
				byte[] data = flog.getActivelink();
				if (data != null) {
					ctx.sendReplyHeaders(200, "OK", new MultiValueTable<String, String>(), "image/png", data.length);
					ctx.writeData(data);
				}
			} else if (file.startsWith("/Att-")) {
				final Attachment attachement = flog.getAttachmentByName(file.substring(1));
				if (attachement != null) {
					final String mimeType = DefaultMIMETypes.guessMIMEType(file, true);
					ctx.sendReplyHeaders(200, "OK", new MultiValueTable<String, String>(), mimeType == null ? "application/octet-stream" : mimeType, attachement.getData().length);
					ctx.writeData(attachement.getData());
				}
			} else if (file.equals("/" + VIEW_RAW_DEFAULT_TEMPLATE_URI)) {
				previewTemplate(factory, pageNode, uri, request, ctx);
			} else if (file.equals("/" + VIEW_DEFAULT_CSS_URI)) {
				previewCSS(factory, pageNode, uri, request, ctx);
			} else if (file.startsWith("/index") && file.endsWith(".xml")) {
				IndexBuilder builder = new IndexBuilder(flog, new FlogFactory(flog).parseAllFlog());
				if(file.equals("/index.xml")) {
					byte[] data = builder.getIndexIndex().getBytes();
					ctx.sendReplyHeaders(200, "OK", new MultiValueTable<String, String>(), "application/xml", data.length);
					ctx.writeData(data);
				} else if(file.matches("^/index_[0-9a-f]\\.xml$")) {
					byte sub = Byte.valueOf(file.replace("/index_", "").replace(".xml", ""), 16);
					byte[] data = builder.getSubIndex(sub).getBytes();
					ctx.sendReplyHeaders(200, "OK", new MultiValueTable<String, String>(), "application/xml", data.length);
					ctx.writeData(data);
				}
			} else {
				this.sendErrorPage(ctx, 404, "Not found", "Unintelligible URI.");
			}
		}
	}

	public static void previewTemplate(FlogFactory factory, PageNode pageNode, URI uri, HTTPRequest request, ToadletContext ctx) throws ToadletContextClosedException, IOException {
		byte[] data = factory.getTemplate().getBytes();
		ctx.sendReplyHeaders(200, "OK", new MultiValueTable<String, String>(), "text/plain", data.length);
		ctx.writeData(data);
	}

	public static void previewCSS(FlogFactory factory, PageNode pageNode, URI uri, HTTPRequest request, ToadletContext ctx) throws ToadletContextClosedException, IOException {
		byte[] data = factory.getCSS().getBytes();
		ctx.sendReplyHeaders(200, "OK", new MultiValueTable<String, String>(), "text/plain", data.length);
		ctx.writeData(data);
	}

	private static String appendPreviewWarning(String page) {
		return page.replaceFirst("</head>(\\s*(\n)\\s*)*<body>", "</head>" +
				"$1<body style=\"margin-top: 25px;\">" +
				"<div style=\"position: absolute; background-color: yellow; color: black; font-size: 10pt;" +
				"border-bottom: 1px solid black; left: 0; right: 0; top: 0;" +
				"height: 25px;\"><p style=\"margin: 0; padding-left: 5px;" +
				" padding-top: 5px;\">" + FlogHelper.getBaseL10n().getString("PreviewWarning") +
				" <strong><a href=\"" + FlogHelperToadlet.BASE_URI + FlogListToadlet.MY_URI + "\">" +
				FlogHelper.getBaseL10n().getString("ReturnToFlogList") + "</a></strong></p></div>");
	}
}

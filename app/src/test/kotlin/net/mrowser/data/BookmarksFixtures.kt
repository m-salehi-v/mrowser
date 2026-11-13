package net.mrowser.data

/** Shared test input: a Chrome-style bookmarks.html export. */
object BookmarksFixtures {
    val chrome = """
<!DOCTYPE NETSCAPE-Bookmark-file-1>
<META HTTP-EQUIV="Content-Type" CONTENT="text/html; charset=UTF-8">
<TITLE>Bookmarks</TITLE><H1>Bookmarks</H1>
<DL><p>
  <DT><H3 ADD_DATE="1">Bookmarks bar</H3>
  <DL><p>
    <DT><A HREF="https://www.star.gr/tv/live-stream" ADD_DATE="1" ICON="data:image/png;base64,AAAA">Star &amp; Live</A>
    <DT><H3>Nested</H3>
    <DL><p>
      <DT><a href='https://example.com/watch?v=1&amp;t=2'>  </a>
      <HR>
      <DT><A HREF="javascript:alert(1)">bad</A>
      <DT><A HREF="ftp://files.example.com/x">ftp</A>
    </DL><p>
  </DL><p>
  <DT><A HREF="http://old.example.org/">Old &#39;one&#39; &#x26; &lt;b&gt;</A>
</DL><p>
""".trimIndent()
}

package org.jahia.modules.contentintegrity.services.checks;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ErrorCollector;

import static org.hamcrest.CoreMatchers.is;

public class ReferencesSanityCheckTest {

    private static final String LINK_1 = "##cms-context##/{mode}/{lang}/##ref:link1##.html";
    private static final String LINK_2 = "##cms-context##/{mode}/{lang}/##ref:link2##.html";

    // ErrorCollector reports every failed assertion of a test method, instead of stopping at the first one.
    @Rule
    public final ErrorCollector collector = new ErrorCollector();

    @Test
    public void unwrapsTheBrokenLinkAndKeepsItsText() {
        assertRemoved("<p>Read the <strong>guide</strong> now</p>",
                "<p>Read the <a href=\"" + LINK_1 + "\" title=\"Guide\"><strong>guide</strong></a> now</p>", 1);
    }

    @Test
    public void unwrapsEveryOccurrenceOfTheBrokenLink() {
        assertRemoved("<p>one, two</p>", "<p><a href=\"" + LINK_1 + "\">one</a>, <a href=\"" + LINK_1 + "\">two</a></p>", 1);
    }

    @Test
    public void keepsTheOtherLinks() {
        final String valid = "<a href=\"" + LINK_2 + "\">valid</a>";
        assertRemoved("broken " + valid, "<a href=\"" + LINK_1 + "\">broken</a> " + valid, 1);
    }

    @Test
    public void doesNotMistakeALinkForAnotherOneWithALongerIndex() {
        final String link10 = "<a href=\"##cms-context##/{mode}/{lang}/##ref:link10##.html\">ten</a>";
        assertRemoved(link10, link10, 1);
    }

    @Test
    public void removesAnElementWithoutContent() {
        assertRemoved("<p>before  after</p>", "<p>before <img alt=\"\" src=\"##doc-context##/{workspace}/##ref:link1##\" /> after</p>", 1);
        assertRemoved("<p>before  after</p>", "<p>before <img src=\"##doc-context##/{workspace}/##ref:link1##\"> after</p>", 1);
    }

    @Test
    public void unwrapsAnElementUpToItsOwnEndTag() {
        assertRemoved("<div>a <div>b</div> c</div>",
                "<div><div data-link=\"" + LINK_1 + "\">a <div>b</div> c</div></div>", 1);
    }

    @Test
    public void removesTheStartTagWhenTheEndTagIsMissing() {
        assertRemoved("<p>text</p>", "<p><a href=\"" + LINK_1 + "\">text</p>", 1);
    }

    @Test
    public void leavesATextWithoutTheLinkUnchanged() {
        assertRemoved("<p>no link</p>", "<p>no link</p>", 1);
    }

    private void assertRemoved(String expected, String html, long index) {
        collector.checkThat(html, ReferencesSanityCheck.removeLink(html, index), is(expected));
    }
}

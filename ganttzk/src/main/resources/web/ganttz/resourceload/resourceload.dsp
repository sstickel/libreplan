<%@ taglib uri="http://www.zkoss.org/dsp/web/core" prefix="c" %>
<%@ taglib uri="http://www.zkoss.org/dsp/zk/core" prefix="z" %>

<c:set var="self" value="${requestScope.arg.self}"/>
<div id="${self.uuid}" class="row_resourceload resourceload-${self.resourceLoadType}"
    z.autoz="true" ${self.outerAttrs}">
    <span class="resourceload_name">${self.resourceLoadName}</span>
    <c:forEach var="child" items="${self.children}">
        ${z:redraw(child, null)}
    </c:forEach>
</div>

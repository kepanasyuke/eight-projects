(function () {
    "use strict";

    var icons = {
        "grid": "<rect x='3' y='3' width='7' height='7' rx='1.5'/><rect x='14' y='3' width='7' height='7' rx='1.5'/><rect x='3' y='14' width='7' height='7' rx='1.5'/><rect x='14' y='14' width='7' height='7' rx='1.5'/>",
        "terminal": "<rect x='2.5' y='4' width='19' height='16' rx='2.5'/><path d='M6.5 9.5 9 12l-2.5 2.5'/><path d='M12 15h5'/>",
        "window": "<rect x='3' y='4' width='18' height='16' rx='2'/><path d='M3 8.5h18'/>",
        "api": "<path d='M8.5 4.5C5.5 6 4 9 4 12s1.5 6 4.5 7.5'/><path d='M15.5 4.5C18.5 6 20 9 20 12s-1.5 6-4.5 7.5'/><path d='M13.5 8.5 10.5 15.5'/>",
        "folder": "<path d='M3 7.5A1.5 1.5 0 0 1 4.5 6h4l2 2.5h7A1.5 1.5 0 0 1 19 10v7a1.5 1.5 0 0 1-1.5 1.5h-13A1.5 1.5 0 0 1 3 17Z'/>",
        "gamepad": "<rect x='2.5' y='7.5' width='19' height='9.5' rx='4'/><path d='M7.5 10.5v3M6 12h3'/><circle cx='16' cy='11.5' r='.9'/><circle cx='18' cy='13.5' r='.9'/>",
        "sand": "<path d='M4 6h16l-6 6v6l-4-2v-4Z'/>",
        "arrow-right": "<path d='M5 12h13'/><path d='m13 6.5 5.5 5.5L13 17.5'/>",
        "arrow-left": "<path d='M19 12H6'/><path d='M11 6.5 5.5 12 11 17.5'/>",
        "external": "<path d='M14 4h6v6'/><path d='M20 4 11 13'/><path d='M18 14v4.5A1.5 1.5 0 0 1 16.5 20h-11A1.5 1.5 0 0 1 4 18.5v-11A1.5 1.5 0 0 1 5.5 6H10'/>",
        "mail": "<rect x='2.5' y='5' width='19' height='14' rx='2'/><path d='m3 7 9 6 9-6'/>",
        "database": "<ellipse cx='12' cy='6' rx='8' ry='3'/><path d='M4 6v6c0 1.7 3.6 3 8 3s8-1.3 8-3V6'/><path d='M4 12v6c0 1.7 3.6 3 8 3s8-1.3 8-3v-6'/>",
        "transfer": "<path d='M4 8.5h13'/><path d='m14 5 3.5 3.5L14 12'/><path d='M20 15.5H7'/><path d='m10 12 -3.5 3.5L10 19'/>",
        "threads": "<circle cx='6' cy='6' r='2.5'/><circle cx='6' cy='18' r='2.5'/><circle cx='18' cy='12' r='2.5'/><path d='M8.5 7.5 15.5 11M8.5 16.5 15.5 13'/>",
        "queue": "<rect x='3' y='4.5' width='12' height='4' rx='1.5'/><rect x='5' y='10' width='12' height='4' rx='1.5'/><rect x='7' y='15.5' width='12' height='4' rx='1.5'/>",
        "code": "<path d='m8 8-4 4 4 4'/><path d='m16 8 4 4-4 4'/><path d='m13.5 6-3 12'/>",
        "shield": "<path d='M12 3.5 19 6v6c0 4.2-3 7.2-7 8.5-4-1.3-7-4.3-7-8.5V6Z'/><path d='m9 12 2.2 2.2L15.5 10'/>",
        "plug": "<path d='M9 3v5M15 3v5'/><path d='M6.5 8h11v2.5a5.5 5.5 0 0 1-11 0Z'/><path d='M12 16v5'/>",
        "refresh": "<path d='M19 12a7 7 0 0 1-12 4.9L4.5 14.5'/><path d='M5 12a7 7 0 0 1 12-4.9L19.5 9.5'/><path d='M4.5 19v-4.5H9'/><path d='M19.5 5v4.5H15'/>",
        "plus": "<path d='M12 5v14M5 12h14'/>",
        "search": "<circle cx='11' cy='11' r='6.5'/><path d='m16 16 4.5 4.5'/>",
        "check": "<path d='m5 12.5 4.5 4.5L19 7'/>",
        "chevron-right": "<path d='m9 6 6 6-6 6'/>",
        "copy": "<rect x='9' y='9' width='11' height='11' rx='2'/><path d='M15 6.5V6a2 2 0 0 0-2-2H6a2 2 0 0 0-2 2v7a2 2 0 0 0 2 2h.5'/>",
        "clock": "<circle cx='12' cy='12' r='8'/><path d='M12 7.5V12l3 2'/>",
        "list": "<path d='M8 6.5h12M8 12h12M8 17.5h12'/><circle cx='4.5' cy='6.5' r='1'/><circle cx='4.5' cy='12' r='1'/><circle cx='4.5' cy='17.5' r='1'/>",
        "play": "<path d='M8 5.5 18 12 8 18.5Z'/>",
        "save": "<path d='M5 4.5h10L19.5 9v10.5a1 1 0 0 1-1 1h-13a1 1 0 0 1-1-1v-14a1 1 0 0 1 1-1Z'/><path d='M8 4.5v5h7v-5'/><rect x='8' y='13' width='8' height='7.5'/>",
        "settings": "<circle cx='12' cy='12' r='3'/><path d='M12 3.5v2.5M12 18v2.5M4.9 7.8l2.1 1.2M17 15l2.1 1.2M4.9 16.2 7 15M17 9l2.1-1.2'/>",
        "close": "<path d='m6 6 12 12M18 6 6 18'/>",
        "minimize": "<path d='M5 12h14'/>",
        "maximize": "<rect x='6' y='6' width='12' height='12' rx='1'/>"
    };

    function build() {
        if (document.getElementById("mt-icon-sprite")) {
            return;
        }
        var markup = ['<svg id="mt-icon-sprite" aria-hidden="true" focusable="false" style="position:absolute;width:0;height:0;overflow:hidden">'];
        Object.keys(icons).forEach(function (name) {
            markup.push('<symbol id="i-' + name + '" viewBox="0 0 24 24">' + icons[name] + "</symbol>");
        });
        markup.push("</svg>");
        var holder = document.createElement("div");
        holder.innerHTML = markup.join("");
        document.body.insertBefore(holder.firstChild, document.body.firstChild);
    }

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", build);
    } else {
        build();
    }
})();

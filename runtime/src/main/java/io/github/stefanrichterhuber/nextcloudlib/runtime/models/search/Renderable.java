package io.github.stefanrichterhuber.nextcloudlib.runtime.models.search;

public interface Renderable {

    StringBuilder render(StringBuilder sb, int indent);

    default String indent(int indent) {
        return " ".repeat(indent * 4);
    }
}

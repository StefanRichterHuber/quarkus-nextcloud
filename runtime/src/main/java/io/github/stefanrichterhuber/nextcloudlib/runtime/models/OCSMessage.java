package io.github.stefanrichterhuber.nextcloudlib.runtime.models;

public record OCSMessage<T>(OCSMessage.OCS<T> ocs) {
    public static record OCS<T>(OCS.Meta meta, T data) {

        public boolean isOk() {
            return meta != null && meta.isOk();
        }

        public static record Meta(String status, int statuscode, String message, String totalitems,
                String itemsperpage) {

            public boolean isOk() {
                return statuscode == 100;
            }
        }
    }
}
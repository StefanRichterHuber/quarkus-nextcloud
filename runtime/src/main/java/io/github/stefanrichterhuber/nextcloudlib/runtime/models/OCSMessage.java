package io.github.stefanrichterhuber.nextcloudlib.runtime.models;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public record OCSMessage<T>(OCSMessage.OCS<T> ocs) {
    /**
     * Checks if the response is successful based on the status code in the meta
     * information.
     * A status code of 200 or 100 is considered successful.
     * 
     * @return
     */
    public boolean isOk() {
        return ocs != null && ocs.isOk();
    }

    public static record OCS<T>(OCS.Meta meta, T data) {

        /**
         * Checks if the response is successful based on the status code in the meta
         * information.
         * A status code of 200 or 100 is considered successful.
         * 
         * @return
         */
        public boolean isOk() {
            return meta != null && meta.isOk();
        }

        public static record Meta(String status, int statuscode, String message, String totalitems,
                String itemsperpage) {

            /**
             * Checks if the response is successful based on the status code in the meta
             * information.
             * A status code of 200 or 100 is considered successful.
             * 
             * @return
             */
            public boolean isOk() {
                return statuscode == 200 || statuscode == 100;
            }
        }
    }
}
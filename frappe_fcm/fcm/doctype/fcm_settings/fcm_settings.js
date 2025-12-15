// Copyright (c) 2025, Frappe FCM Contributors
// For license information, please see license.txt

frappe.ui.form.on("FCM Settings", {
    refresh: function(frm) {
        // Add test connection button handler
        frm.add_custom_button(__("Test Connection"), function() {
            frappe.call({
                method: "frappe_fcm.fcm.doctype.fcm_settings.fcm_settings.test_fcm_connection",
                freeze: true,
                freeze_message: __("Testing FCM connection..."),
                callback: function(r) {
                    if (r.message) {
                        if (r.message.success) {
                            frappe.msgprint({
                                title: __("Success"),
                                indicator: "green",
                                message: r.message.message +
                                    (r.message.project_id ? "<br><br>Project ID: " + r.message.project_id : "") +
                                    "<br>API Type: " + (r.message.api_type || "unknown")
                            });
                        } else {
                            frappe.msgprint({
                                title: __("Connection Failed"),
                                indicator: "red",
                                message: r.message.message
                            });
                        }
                    }
                }
            });
        }, __("Actions"));

        // Add send test notification button
        frm.add_custom_button(__("Send Test Notification"), function() {
            frappe.call({
                method: "frappe_fcm.fcm.doctype.fcm_settings.fcm_settings.send_test_notification",
                freeze: true,
                freeze_message: __("Sending test notification..."),
                callback: function(r) {
                    if (r.message) {
                        let msg = __("Sent: {0}, Failed: {1}", [r.message.success, r.message.failed]);
                        if (r.message.failed_users && r.message.failed_users.length > 0) {
                            msg += "<br><br>" + __("Failed for: ") + r.message.failed_users.join(", ");
                        }
                        frappe.msgprint({
                            title: r.message.success > 0 ? __("Test Notification Sent") : __("Send Failed"),
                            indicator: r.message.success > 0 ? "green" : "red",
                            message: msg
                        });
                    }
                }
            });
        }, __("Actions"));
    },

    fcm_test_button: function(frm) {
        frm.trigger("test_connection");
    }
});

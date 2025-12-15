// Copyright (c) 2025, Frappe FCM Contributors
// For license information, please see license.txt

frappe.ui.form.on("FCM Settings", {
    refresh: function(frm) {
        // Add primary button at top of page
        frm.page.set_primary_action(__("Test FCM Connection"), function() {
            frm.trigger("run_test_connection");
        }, "fa fa-check");

        // Add secondary button for sending test notification
        frm.page.set_secondary_action(__("Send Test Notification"), function() {
            frm.trigger("run_send_test");
        });

        // Also add as menu items for easier access
        frm.add_custom_button(__("Test Connection"), function() {
            frm.trigger("run_test_connection");
        }, __("FCM Actions"));

        frm.add_custom_button(__("Send Test Notification"), function() {
            frm.trigger("run_send_test");
        }, __("FCM Actions"));

        // Update status display on refresh
        frm.trigger("update_status_display");
    },

    run_test_connection: function(frm) {
        frappe.call({
            method: "frappe_fcm.fcm.doctype.fcm_settings.fcm_settings.test_fcm_connection",
            freeze: true,
            freeze_message: __("Testing FCM connection..."),
            callback: function(r) {
                if (r.message) {
                    frm.trigger("show_connection_result", r.message);
                }
            },
            error: function(r) {
                frm.trigger("show_connection_result", {
                    success: false,
                    message: __("Error testing connection. Please check your configuration.")
                });
            }
        });
    },

    run_send_test: function(frm) {
        frappe.call({
            method: "frappe_fcm.fcm.doctype.fcm_settings.fcm_settings.send_test_notification",
            freeze: true,
            freeze_message: __("Sending test notification to all devices..."),
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
            },
            error: function() {
                frappe.msgprint({
                    title: __("Error"),
                    indicator: "red",
                    message: __("Failed to send test notification. Check if FCM is enabled and configured.")
                });
            }
        });
    },

    show_connection_result: function(frm, result) {
        // Show msgprint
        frappe.msgprint({
            title: result.success ? __("Connection Successful") : __("Connection Failed"),
            indicator: result.success ? "green" : "red",
            message: result.message +
                (result.project_id ? "<br><br><b>Project ID:</b> " + result.project_id : "") +
                (result.api_type ? "<br><b>API Type:</b> " + result.api_type : "")
        });

        // Update inline status
        frm.trigger("update_status_display", result);
    },

    update_status_display: function(frm, result) {
        let status_html = "";

        if (result) {
            if (result.success) {
                status_html = `
                    <div style="background: #d4edda; border: 1px solid #c3e6cb; border-radius: 8px; padding: 15px; margin-top: 10px;">
                        <div style="display: flex; align-items: center;">
                            <span style="font-size: 24px; margin-right: 10px;">✅</span>
                            <div>
                                <strong style="color: #155724; font-size: 16px;">Connection Successful</strong>
                                <p style="margin: 5px 0 0 0; color: #155724;">${result.message}</p>
                                ${result.project_id ? `<p style="margin: 5px 0 0 0; color: #666;"><b>Project:</b> ${result.project_id}</p>` : ""}
                            </div>
                        </div>
                    </div>`;
            } else {
                status_html = `
                    <div style="background: #f8d7da; border: 1px solid #f5c6cb; border-radius: 8px; padding: 15px; margin-top: 10px;">
                        <div style="display: flex; align-items: center;">
                            <span style="font-size: 24px; margin-right: 10px;">❌</span>
                            <div>
                                <strong style="color: #721c24; font-size: 16px;">Connection Failed</strong>
                                <p style="margin: 5px 0 0 0; color: #721c24;">${result.message}</p>
                            </div>
                        </div>
                    </div>`;
            }
        } else {
            status_html = `
                <div style="background: #e2e3e5; border: 1px solid #d6d8db; border-radius: 8px; padding: 15px; margin-top: 10px;">
                    <div style="display: flex; align-items: center;">
                        <span style="font-size: 24px; margin-right: 10px;">ℹ️</span>
                        <div>
                            <strong style="color: #383d41; font-size: 16px;">Not Tested</strong>
                            <p style="margin: 5px 0 0 0; color: #383d41;">Click "Test FCM Connection" button above to verify your configuration.</p>
                        </div>
                    </div>
                </div>`;
        }

        frm.fields_dict.fcm_connection_status.$wrapper.html(status_html);
    },

    // Handle the inline button click
    fcm_test_button: function(frm) {
        frm.trigger("run_test_connection");
    }
});

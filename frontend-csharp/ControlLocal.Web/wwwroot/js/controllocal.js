window.controlLocalDialog = {
    show: (dialog) => {
        if (!dialog) return;
        if (!dialog.open) {
            dialog.showModal();
        }
    },
    close: (dialog) => {
        if (!dialog) return;
        if (dialog.open) {
            dialog.close();
        }
    }
};

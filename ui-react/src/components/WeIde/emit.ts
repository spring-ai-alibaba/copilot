export const openFile = (path: string, line?: number) => {
    const event = new CustomEvent('openFile', {
        detail: { path, line }
    });
    window.dispatchEvent(event);
};
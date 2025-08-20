export function getDefaultContent(fileName: string): string {
  const extension = fileName.split(".").pop()?.toLowerCase();

  switch (extension) {
    case "html":
      return ``;

    case "tsx":
    case "ts":
      return "";

    case "css":
      return "";
    case "json":
      return `{

}`;

    case "md":
      return ``;

    default:
      return "";
  }
}

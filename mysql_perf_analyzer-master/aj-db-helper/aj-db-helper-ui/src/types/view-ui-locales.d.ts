declare module "view-ui-plus/dist/locale/*.js" {
  interface MessageTree { [key: string]: string | MessageTree; }
  const messages: { i: MessageTree };
  export default messages;
}

export type ActionType = 'file' | 'shell';

export interface BaseAction {
  content: string;
}

export interface FileAction extends BaseAction {
  type: 'file';
  filePath: string;
}

export interface ShellAction extends BaseAction {
  type: 'shell';
}

export interface StartAction extends BaseAction {
  type: 'start';
}

export type BoltAction = FileAction | ShellAction | StartAction;

export type BoltActionData = BoltAction | BaseAction;

const ARTIFACT_TAG_OPEN = '<boltArtifact';
const ARTIFACT_TAG_CLOSE = '</boltArtifact>';
const ARTIFACT_ACTION_TAG_OPEN = '<boltAction';
const ARTIFACT_ACTION_TAG_CLOSE = '</boltAction>';
export function unreachable(message: string): never {
  throw new Error(`Unreachable: ${message}`);
}

export interface BoltArtifactData {
  id: string;
  title: string;
}

export interface ArtifactCallbackData extends BoltArtifactData {
  messageId: string;
  action?: {
    type?: 'file' | 'shell';
    filePath?: string;
    content?: string;
  }
}

export interface ActionCallbackData {
  artifactId: string;
  messageId: string;
  actionId: string;
  action: BoltAction;
}

export type ArtifactCallback = (data: ArtifactCallbackData) => void;
export type ActionCallback = (data: ActionCallbackData) => void;

export interface ParserCallbacks {
  onArtifactOpen?: ArtifactCallback;
  onArtifactClose?: ArtifactCallback;
  onActionOpen?: ActionCallback;
  onActionStream?: ActionCallback;
  onActionClose?: ActionCallback;
}

interface ElementFactoryProps {
  messageId: string;
}

type ElementFactory = (props: ElementFactoryProps) => string;

export interface StreamingMessageParserOptions {
  callbacks?: ParserCallbacks;
  artifactElement?: ElementFactory;
}

interface MessageState {
  position: number;
  insideArtifact: boolean;
  insideAction: boolean;
  currentArtifact?: BoltArtifactData;
  currentAction: BoltActionData;
  actionId: number;
  hasInstallExecuted?: boolean;
  isUseStartCommand?: boolean;
}

export class StreamingMessageParser {
  private messages = new Map<string, MessageState>();
  public isUseStartCommand = false;
  constructor(private options: StreamingMessageParserOptions = {}) { }

  parse(messageId: string, input: string) {
    let state = this.messages.get(messageId);

    if (!state) {
      state = {
        position: 0,
        insideAction: false,
        insideArtifact: false,
        currentAction: { content: '' },
        actionId: 0,
        hasInstallExecuted: false,
      };
      this.messages.set(messageId, state);
    }

    let output = '';
    const regex = {
      artifactOpen: /<boltArtifact[^>]*>/g,
      artifactClose: /<\/boltArtifact>/g,
      actionOpen: /<boltAction[^>]*>/g,
      actionClose: /<\/boltAction>/g
    };

    const allActionData = {}

    while (state.position < input.length) {
      if (state.insideArtifact) {
        if (state.insideAction) {
          // 查找动作结束标签
          regex.actionClose.lastIndex = state.position;
          const actionCloseMatch = regex.actionClose.exec(input);
          
          if (actionCloseMatch) {
            const content = input.slice(state.position, actionCloseMatch.index);
            
            // 处理 file 和 shell 类型的 action
            if ('type' in state.currentAction) {
              const actionData = {
                artifactId: state.currentArtifact!.id,
                messageId,
                actionId: String(state.actionId - 1),
                action: {
                  ...state.currentAction,
                  content,
                },
              };


              // 根据 action 类型调用不同的回调
              if (state.currentAction.type === 'file') {

                allActionData[state.currentAction.filePath] = actionData;

              } else if (state.currentAction.type === 'shell' || 'start') {
                // shell 类型只在关闭时处理
                this.options.callbacks?.onActionClose?.(actionData);
              } 
            }

            state.position = actionCloseMatch.index + actionCloseMatch[0].length;
            state.insideAction = false;
          } else {
            // 只对 file 类型进行流式处理
            const remainingContent = input.slice(state.position);
            if ('type' in state.currentAction && state.currentAction.type === 'file' && !allActionData[state.currentAction.filePath]) {
              allActionData[state.currentAction.filePath] = {
                artifactId: state.currentArtifact!.id,
                messageId,
                actionId: String(state.actionId - 1),
                action: {
                  ...state.currentAction as FileAction,
                  content: remainingContent,
                  filePath: state.currentAction.filePath,
                },
              }
              // this.options.callbacks?.onActionStream?.({
              //   artifactId: state.currentArtifact!.id,
              //   messageId,
              //   actionId: String(state.actionId - 1),
              //   action: {
              //     ...state.currentAction as FileAction,
              //     content: remainingContent,
              //     filePath: state.currentAction.filePath,
              //   },
              // });
            }
            break;
          }
        } else {
          // 查找下一个动作开始标签或者 artifact 结束标签
          const nextActionMatch = regex.actionOpen.exec(input.slice(state.position));
          const artifactCloseMatch = regex.artifactClose.exec(input.slice(state.position));
          
          if (nextActionMatch && (!artifactCloseMatch || nextActionMatch.index < artifactCloseMatch.index)) {
            const actionTag = nextActionMatch[0];
            state.currentAction = this.parseActionTag(actionTag);
            state.insideAction = true;
            state.position += nextActionMatch.index + nextActionMatch[0].length;
            
            this.options.callbacks?.onActionOpen?.({
              artifactId: state.currentArtifact!.id,
              messageId,
              actionId: String(state.actionId++),
              action: state.currentAction as BoltAction,
            });
          } else if (artifactCloseMatch) {
            state.position += artifactCloseMatch.index + artifactCloseMatch[0].length;
            state.insideArtifact = false;
            this.options.callbacks?.onArtifactClose?.({ 
              messageId, 
              ...state.currentArtifact! 
            });
          } else {
            break;
          }
        }
      } else {
        // 查找 artifact 开始标签
        const artifactMatch = regex.artifactOpen.exec(input.slice(state.position));
        if (artifactMatch) {
          output += input.slice(state.position, state.position + artifactMatch.index);
          const artifactTag = artifactMatch[0];
          
          const artifactTitle = this.extractAttribute(artifactTag, 'title');
          const artifactId = this.extractAttribute(artifactTag, 'id');
          
          state.currentArtifact = {
            id: artifactId!,
            title: artifactTitle!,
          };
          
          state.insideArtifact = true;
          state.position += artifactMatch.index + artifactMatch[0].length;
          
          this.options.callbacks?.onArtifactOpen?.({ 
            messageId, 
            ...state.currentArtifact 
          });
          
          const artifactFactory = this.options.artifactElement ?? createArtifactElement;
          output += artifactFactory({ messageId });
        } else {
          output += input.slice(state.position);
          break;
        }
      }
    }

    Object.keys(allActionData).forEach(key => {
      this.options.callbacks?.onActionStream?.(allActionData[key]);
    });

    return output;
  }

  reset() {
    this.messages.clear();
  }

  private parseActionTag(actionTag: string) {
    const actionType = this.extractAttribute(actionTag, 'type') as ActionType;
    const filePath = this.extractAttribute(actionTag, 'filePath');

    if (!actionType) {
      console.warn('Action type not specified');
      return { type: 'file', content: '', filePath: '' } as FileAction;
    }

    const actionAttributes = {
      type: actionType,
      content: '',
    };

    if (actionType === 'file') {
      if (!filePath) {
        console.debug('File path not specified');
      }
      (actionAttributes as FileAction).filePath = filePath || '';
    } else if (!(['shell', 'start'].includes(actionType))) {
      console.warn(`Unknown action type '${actionType}'`);
      return { type: 'file', content: '', filePath: '' } as FileAction;
    }

    return actionAttributes as FileAction | ShellAction;
  }

  private extractAttribute(tag: string, attributeName: string): string | undefined {
    const match = tag.match(new RegExp(`${attributeName}="([^"]*)"`, 'i'));
    return match ? match[1] : undefined;
  }

  getMessageState(messageId: string): MessageState | undefined {
    return this.messages.get(messageId);
  }
}

const createArtifactElement: ElementFactory = (props) => {
  const elementProps = [
    'class="__boltArtifact__"',
    ...Object.entries(props).map(([key, value]) => {
      return `data-${camelToDashCase(key)}=${JSON.stringify(value)}`;
    }),
  ];

  return `<div ${elementProps.join(' ')}></div>`;
};

function camelToDashCase(input: string) {
  return input.replace(/([a-z])([A-Z])/g, '$1-$2').toLowerCase();
}

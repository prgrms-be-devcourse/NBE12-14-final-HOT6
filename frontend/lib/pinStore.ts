// 서버 검증을 통과한 간편비밀번호를 결제 요청에 실어 보내기 위해 화면 이동 사이에만 메모리에 둔다.
// 저장소(localStorage·sessionStorage)에는 남기지 않는다. 새로고침하면 사라지고 다시 입력한다.
let verified: string | null = null;

export const holdPin = (pin: string) => { verified = pin; };
export const peekPin = () => verified;
export const dropPin = () => { verified = null; };

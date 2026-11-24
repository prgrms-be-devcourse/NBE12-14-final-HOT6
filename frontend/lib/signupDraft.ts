// 회원가입은 1단계(정보 입력)와 2단계(간편비밀번호 설정)를 거친 뒤 한 번에 요청한다.
// 비밀번호를 저장소에 남기지 않으려고 화면 이동 사이에는 메모리에만 둔다 (새로고침하면 1단계부터 다시).
export type SignupDraft = { name: string; email: string; phone: string; password: string };
let draft: SignupDraft | null = null;

export const saveSignupDraft = (d: SignupDraft) => { draft = d; };
export const loadSignupDraft = () => draft;
export const clearSignupDraft = () => { draft = null; };

// 서버의 가입 규칙과 같다 (SignupRequest)
export const PASSWORD_RULE = /^(?=.*[A-Za-z])(?=.*\d)(?=.*[!@#%^&*])[A-Za-z\d!@#%^&*]{8,15}$/;
export const PHONE_RULE = /^01\d{8,9}$/;

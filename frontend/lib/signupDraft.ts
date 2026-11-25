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
export const NAME_RULE = /^([가-힣]+|[A-Za-z]+( [A-Za-z]+)*)$/; // 한글만 또는 영문만. 앞뒤 공백을 지운 값에 적용한다
// 입력하는 순간 지우는 문자. 한글을 조합하는 중에 나오는 낱자(천지인의 점 포함)는 남겨 두고, 제출할 때 NAME_RULE 로 거른다
const NAME_BLOCKED = /[^가-힣ㄱ-ㅎㅏ-ㅣ\u318D\u119E\u11A2A-Za-z ]/g;
const HAS_HANGUL = /[가-힣ㄱ-ㅎㅏ-ㅣ]/;
export const cleanName = (v: string) => {
  const name = v.replace(NAME_BLOCKED, "").replace(/ {2,}/g, " ").replace(/^ /, "");
  return HAS_HANGUL.test(name) ? name.replace(/ /g, "") : name; // 띄어쓰기는 영문 이름만
};

from flask import Flask, make_response, jsonify, request

app = Flask(__name__)

state = {
    'content': ''
}


@app.route('/')
def get_cont():
    res = make_response(jsonify(state['content']), 200)
    return res


# PUT ?content=asd
@app.route('/', methods=["PUT"])
def set_cont():
    for k, v in request.args.items():
        if k == 'content':
            state['content'] = v
    res = make_response(state['content'], 200)
    return res


@app.route('/', methods=["DELETE"])
def del_content():
    state['content'] = ''
    res = make_response(state['content'], 200)
    return res


if __name__ == "__main__":
    app.run(debug=True, host='0.0.0.0')